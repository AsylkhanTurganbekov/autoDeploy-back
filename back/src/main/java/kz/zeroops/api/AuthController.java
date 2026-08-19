package kz.zeroops.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
  private final AppUserRepository users; private final PasswordEncoder passwords; private final JwtService jwt;
  public AuthController(AppUserRepository users, PasswordEncoder passwords, JwtService jwt){this.users=users;this.passwords=passwords;this.jwt=jwt;}
  @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public AuthResponse register(@Valid @RequestBody Credentials input){ if(users.existsByEmailIgnoreCase(input.email())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already exists"); AppUser user=users.save(new AppUser(input.email().toLowerCase(),passwords.encode(input.password()),UserRole.DEVELOPER)); return AuthResponse.from(user,jwt.issue(user)); }
  @PostMapping("/login") public AuthResponse login(@Valid @RequestBody Credentials input){ AppUser user=users.findByEmailIgnoreCase(input.email()).filter(u->passwords.matches(input.password(),u.getPasswordHash())).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials")); return AuthResponse.from(user,jwt.issue(user)); }
  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout() { /* JWT is stateless; client discards it. */ }
  public record Credentials(@Email @NotBlank String email,@NotBlank String password){}
  public record AuthResponse(Long id,String email,UserRole role,String accessToken){static AuthResponse from(AppUser user,String token){return new AuthResponse(user.getId(),user.getEmail(),user.getRole(),token);}}
}
