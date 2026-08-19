package kz.zeroops.api;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="github_connections")
public class GitHubConnection {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="owner_id",nullable=false) private AppUser owner;
 @Column(nullable=false,length=255) private String account;
 @Column(name="token_ciphertext",nullable=false,columnDefinition="TEXT") private String tokenCiphertext;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 protected GitHubConnection(){} public GitHubConnection(AppUser owner,String account,String tokenCiphertext){this.owner=owner;this.account=account;this.tokenCiphertext=tokenCiphertext;}
 public Long getId(){return id;} public AppUser getOwner(){return owner;} public String getAccount(){return account;} public String getTokenCiphertext(){return tokenCiphertext;} public Instant getCreatedAt(){return createdAt;}
}
