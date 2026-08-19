package kz.zeroops.api;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
  @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwt) throws Exception { return http.csrf(csrf->csrf.disable()).cors(c->{}).headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny()).referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/v1/health","/actuator/**","/api/v1/auth/**","/api/v1/webhooks/**").permitAll().anyRequest().authenticated()).addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build(); }
  @Bean org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(){var c=new org.springframework.web.cors.CorsConfiguration();c.setAllowedOrigins(java.util.List.of("http://localhost:18080","http://127.0.0.1:18080"));c.setAllowedMethods(java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(java.util.List.of("Authorization","Content-Type","Last-Event-ID"));var s=new org.springframework.web.cors.UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}
@Component
class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt; JwtFilter(JwtService jwt){this.jwt=jwt;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException { String value=req.getHeader(HttpHeaders.AUTHORIZATION); if(value!=null&&value.startsWith("Bearer ")) try { String token=value.substring(7); String email=jwt.subject(token); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+jwt.role(token).name())))); } catch(Exception ignored) {} chain.doFilter(req,res); }
}
