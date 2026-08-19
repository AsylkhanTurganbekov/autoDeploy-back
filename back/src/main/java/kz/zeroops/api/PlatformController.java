package kz.zeroops.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only local MVP data for product screens. Replace these adapters with agent/Prometheus sources later. */
@RestController
@RequestMapping("/api/v1")
public class PlatformController {
  private final ProjectRepository projects;
  private final DeploymentRepository deployments;
  public PlatformController(ProjectRepository projects, DeploymentRepository deployments) { this.projects = projects; this.deployments = deployments; }

  @GetMapping("/dashboard")
  public DashboardResponse dashboard() {
    List<Deployment> recent = deployments.findAllByOrderByCreatedAtDesc();
    long failed = recent.stream().filter(d -> d.getStatus() == DeploymentStatus.FAILED).count();
    return new DashboardResponse(projects.count(), recent.stream().filter(d -> d.getStatus() != DeploymentStatus.SUCCESS).count(), 1, failed,
        recent.stream().limit(6).map(DeploymentView::from).toList(), server());
  }

  @GetMapping("/git-connections") public List<GitConnectionResponse> connections() {
    return List.of(new GitConnectionResponse(1L, "GitHub · local MVP", "GITHUB", "CONNECTED", "autodeploy-demo", "Mock connection — OAuth will be added later"));
  }
  @GetMapping("/repositories") public List<RepositoryResponse> repositories() {
    return List.of(new RepositoryResponse("demo/spring-service", "https://github.com/demo/spring-service", "main", "SPRING_BOOT"), new RepositoryResponse("demo/next-store", "https://github.com/demo/next-store", "main", "NODE"));
  }
  @GetMapping("/servers") public List<ServerResponse> servers() { return List.of(server()); }
  @GetMapping("/monitoring") public MonitoringResponse monitoring() {
    return new MonitoringResponse("HEALTHY", "4d 12h", 23, "1.4 / 4 GB", 128, 142, 99.98,
        List.of(18,21,16,24,20,31,27,23,29,22,25,23), List.of(1.1,1.2,1.15,1.3,1.25,1.45,1.38,1.4,1.32,1.5,1.4,1.4), List.of(104,121,110,137,128,152,143,131,166,148,133,128));
  }
  private ServerResponse server() { return new ServerResponse(1L, "prod-server-01", "ONLINE", 32, 48, 62, 6, Instant.now().minus(12, ChronoUnit.SECONDS)); }

  public record DashboardResponse(long projects, long activeDeployments, long servers, long errors, List<DeploymentView> recentDeployments, ServerResponse server) {}
  public record DeploymentView(Long id, String projectSlug, String commitSha, DeploymentStatus status, Instant createdAt) { static DeploymentView from(Deployment d) { return new DeploymentView(d.getId(), d.getProjectSlug(), d.getCommitSha(), d.getStatus(), d.getCreatedAt()); } }
  public record GitConnectionResponse(Long id, String name, String provider, String status, String account, String description) {}
  public record RepositoryResponse(String fullName, String repositoryUrl, String defaultBranch, String suggestedRuntime) {}
  public record ServerResponse(Long id, String name, String status, int cpu, int ram, int disk, int containers, Instant lastHeartbeat) {}
  public record MonitoringResponse(String status, String uptime, int cpu, String ram, int requestsPerMinute, int responseTimeMs, double availability, List<Integer> cpuSeries, List<Double> ramSeries, List<Integer> requestSeries) {}
}
