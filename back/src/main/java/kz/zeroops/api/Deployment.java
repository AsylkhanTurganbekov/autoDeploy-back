package kz.zeroops.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deployments")
public class Deployment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, length = 200) private String projectSlug;
  @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "project_id") private Project project;
  @Column(nullable = false, length = 64) private String commitSha;
  @Column(nullable = false, length = 200) private String servicePath = ".";
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private DeploymentStatus status;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  @Column(nullable = false) private Instant updatedAt = Instant.now();
  @Column(length = 1000) private String failureReason;
  protected Deployment() {}
  public Deployment(String projectSlug, String commitSha) { this.projectSlug = projectSlug; this.commitSha = commitSha; this.status = DeploymentStatus.QUEUED; }
  public void moveTo(DeploymentStatus status) { this.status = status; this.updatedAt = Instant.now(); }
  public void attach(Project project) { this.project = project; }
  public void moveTo(DeploymentStatus status, String failureReason) { this.status = status; this.failureReason = failureReason; this.updatedAt = Instant.now(); }
  /** A lease is only reclaimed after the Agent has been absent long enough to be considered stopped. */
  public void requeueExpiredLease() { moveTo(DeploymentStatus.QUEUED, null); }
  public Long getId() { return id; }
  public String getProjectSlug() { return projectSlug; }
  public String getCommitSha() { return commitSha; }
  public String getServicePath() { return servicePath; }
  public void setServicePath(String servicePath) { this.servicePath = servicePath == null || servicePath.isBlank() ? "." : servicePath; }
  public DeploymentStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public String getFailureReason() { return failureReason; }
  public Project getProject() { return project; }
}
