package kz.zeroops.api;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  public JwtService(@Value("${JWT_SECRET:}") String secret) {
    this.key = secret.isBlank() ? Jwts.SIG.HS256.key().build() : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
  public String issue(AppUser user) { Instant now=Instant.now(); return Jwts.builder().subject(user.getEmail()).claim("role", user.getRole().name()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600))).signWith(key).compact(); }
  public String subject(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
  public UserRole role(String token) { return UserRole.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get("role", String.class)); }
}
