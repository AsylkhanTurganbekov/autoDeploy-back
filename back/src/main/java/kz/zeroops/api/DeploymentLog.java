package kz.zeroops.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deployment_logs")
public class DeploymentLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "deployment_id", nullable = false) private Deployment deployment;
  @Column(nullable = false) private Integer lineNumber;
  @Column(nullable = false) private String message;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  protected DeploymentLog() {}
  public DeploymentLog(Deployment deployment, int lineNumber, String message) { this.deployment = deployment; this.lineNumber = lineNumber; this.message = message; }
  public Long getId() { return id; }
  public Integer getLineNumber() { return lineNumber; }
  public String getMessage() { return message; }
  public Instant getCreatedAt() { return createdAt; }
}
