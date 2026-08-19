package kz.zeroops.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "environment_variables")
public class EnvironmentVariable {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
  @Column(name = "variable_key", nullable = false, length = 128) private String key;
  @Column(name = "variable_value", nullable = false) private String value;
  @Column(name = "is_secret", nullable = false) private boolean secret;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  protected EnvironmentVariable() {}
  public EnvironmentVariable(Project project, String key, String value, boolean secret) { this.project = project; this.key = key; this.value = value; this.secret = secret; }
  public void update(String value, boolean secret) { this.value = value; this.secret = secret; }
  public Long getId() { return id; }
  public String getKey() { return key; }
  public String getValue() { return value; }
  public boolean isSecret() { return secret; }
  public Instant getCreatedAt() { return createdAt; }
}
