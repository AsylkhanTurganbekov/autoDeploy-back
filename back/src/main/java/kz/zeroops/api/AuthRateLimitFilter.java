package kz.zeroops.api;
import jakarta.servlet.FilterChain; import jakarta.servlet.http.HttpServletRequest; import jakarta.servlet.http.HttpServletResponse; import java.io.IOException; import java.time.Instant; import java.util.concurrent.ConcurrentHashMap; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
/** Small in-process throttle for credential endpoints; use a shared limiter at multi-instance scale. */
@Component public class AuthRateLimitFilter extends OncePerRequestFilter {
 private record Window(long started,int requests){} private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 @Override protected boolean shouldNotFilter(HttpServletRequest request){return !(request.getMethod().equals("POST")&&(request.getRequestURI().endsWith("/auth/login")||request.getRequestURI().endsWith("/auth/register")));}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,jakarta.servlet.ServletException {String key=request.getRemoteAddr()+request.getRequestURI();long now=Instant.now().getEpochSecond();Window current=windows.compute(key,(k,v)->v==null||now-v.started>=60?new Window(now,1):new Window(v.started,v.requests+1));if(current.requests>10){response.setStatus(429);response.setContentType("application/problem+json");response.getWriter().write("{\"detail\":\"Too many authentication attempts; try again later\"}");return;}chain.doFilter(request,response);}
}
