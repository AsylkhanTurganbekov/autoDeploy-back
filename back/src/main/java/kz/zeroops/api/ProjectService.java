package kz.zeroops.api;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "project_services")
public class ProjectService {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
  @Column(nullable = false, length = 100) private String serviceKey;
  @Column(nullable = false, length = 200) private String relativePath;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ProjectRuntime runtime;
  @Column(name = "internal_port", nullable = false) private int internalPort;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ServiceVisibility visibility;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private DockerfileSource dockerfileSource;
  @Column(nullable = false) private boolean selected;
  @Column(columnDefinition = "TEXT", nullable = false) private String evidence = "[]";
  @Column(nullable = false) private Instant createdAt = Instant.now();
  @Column(nullable = false) private Instant updatedAt = Instant.now();
  protected ProjectService() { }
  public ProjectService(Project project, String key, String path, ProjectRuntime runtime, int port, ServiceVisibility visibility, DockerfileSource source, boolean selected, String evidence) {
    this.project=project; this.serviceKey=key; this.relativePath=path; this.runtime=runtime; this.internalPort=port; this.visibility=visibility; this.dockerfileSource=source; this.selected=selected; this.evidence=evidence;
  }
  public void select(boolean value) { selected=value; updatedAt=Instant.now(); }
  public Long getId(){return id;} public String getServiceKey(){return serviceKey;} public String getRelativePath(){return relativePath;} public ProjectRuntime getRuntime(){return runtime;} public int getInternalPort(){return internalPort;} public ServiceVisibility getVisibility(){return visibility;} public DockerfileSource getDockerfileSource(){return dockerfileSource;} public boolean isSelected(){return selected;} public String getEvidence(){return evidence;}
}
