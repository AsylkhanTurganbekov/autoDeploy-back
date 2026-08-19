package kz.zeroops.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, length = 100) private String name;
  @Column(nullable = false, length = 63, unique = true) private String slug;
  @Column(nullable = false, length = 500) private String repositoryUrl;
  @Column(nullable = false, length = 255) private String branch;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ProjectRuntime runtime;
  @Column(nullable = false) private Integer applicationPort;
  @Column(length = 255) private String domain;
  @Column(nullable = false) private boolean autoDeploy = true;
  @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "owner_id") private AppUser owner;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected Project() {}
  public Project(String name, String slug, String repositoryUrl, String branch, ProjectRuntime runtime, Integer applicationPort) {
    this.name = name; this.slug = slug; this.repositoryUrl = repositoryUrl; this.branch = branch;
    this.runtime = runtime; this.applicationPort = applicationPort;
  }
  public void configureDelivery(String domain, boolean autoDeploy) { this.domain = domain; this.autoDeploy = autoDeploy; }
  public void update(String name, String repositoryUrl, String branch, ProjectRuntime runtime, Integer applicationPort) {
    this.name = name; this.repositoryUrl = repositoryUrl; this.branch = branch; this.runtime = runtime; this.applicationPort = applicationPort;
  }
  public void assignOwner(AppUser owner) { this.owner = owner; }
  public Long getId() { return id; }
  public String getName() { return name; }
  public String getSlug() { return slug; }
  public String getRepositoryUrl() { return repositoryUrl; }
  public String getBranch() { return branch; }
  public ProjectRuntime getRuntime() { return runtime; }
  public Integer getApplicationPort() { return applicationPort; }
  public String getDomain() { return domain; }
  public boolean isAutoDeploy() { return autoDeploy; }
  public Instant getCreatedAt() { return createdAt; }
  public AppUser getOwner() { return owner; }
}
