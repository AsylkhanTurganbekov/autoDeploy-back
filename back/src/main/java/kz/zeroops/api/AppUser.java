package kz.zeroops.api;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "app_users")
public class AppUser {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, unique = true) private String email;
  @Column(nullable = false) private String passwordHash;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  protected AppUser() {}
  public AppUser(String email, String passwordHash, UserRole role) { this.email=email; this.passwordHash=passwordHash; this.role=role; }
  public Long getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public UserRole getRole(){return role;}
}
