package kz.zeroops.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit Docker opt-in: fixed commands, isolated resources and no user shell input. */
@Component
@ConditionalOnProperty(name = "agent.execution-mode", havingValue = "docker")
class DockerDeploymentExecutor implements DeploymentExecutor {
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  @Override public ExecutionResult execute(AgentBoundary.Manifest manifest, Consumer<String> log) {
    Path workspace = null;
    String network = "autodeploy-net-" + manifest.projectId();
    String name = "autodeploy-" + manifest.projectId() + "-" + manifest.deploymentId();
    String previousImage = null;
    try {
      workspace = Files.createTempDirectory("autodeploy-" + manifest.deploymentId() + "-");
      log.accept("Checking out the verified GitHub commit.");
      if (!run(List.of("git", "clone", "--depth", "1", "--branch", manifest.branch(), manifest.repositoryUrl(), workspace.toString()), log)) return failed("Git checkout failed.");
      if (!run(List.of("git", "-C", workspace.toString(), "checkout", "--detach", manifest.commitSha()), log)) return failed("Requested commit is unavailable.");
      if (!run(List.of("docker", "network", "inspect", network), log)) {
        log.accept("Creating isolated project network.");
        if (!run(List.of("docker", "network", "create", "--label", "io.autodeploy.managed=true", "--label", "io.autodeploy.project=" + manifest.projectId(), network), log)) return failed("Project network cannot be created.");
      }
      log.accept("Building immutable Docker image.");
      if (!run(List.of("docker", "build", "--pull", "--tag", manifest.imageTag(), workspace.toString()), log)) return failed("Docker image build failed.");
      previousImage = previousImage(manifest.projectId(), log);
      stopManagedProject(manifest.projectId(), log);
      if (!start(name, manifest.imageTag(), network, manifest, log)) {
        restore(previousImage, network, manifest, log);
        return failed("Docker rejected the isolated container.");
      }
      if (!healthy(manifest, log)) {
        run(List.of("docker", "rm", "--force", name), log);
        restore(previousImage, network, manifest, log);
        return failed("Health check failed; previous image was restored when available.");
      }
      return new ExecutionResult(true, "Image built, isolated container started and health check passed.", null);
    } catch (IOException e) { return failed("Agent workspace is unavailable."); }
    finally { deleteWorkspace(workspace); }
  }

  private boolean start(String name, String image, String network, AgentBoundary.Manifest manifest, Consumer<String> log) {
    return run(List.of("docker", "run", "--detach", "--name", name,
        "--label", "io.autodeploy.managed=true", "--label", "io.autodeploy.project=" + manifest.projectId(),
        "--label", "io.autodeploy.application-port=" + manifest.applicationPort(), "--label", "io.autodeploy.public-port=" + manifest.publicPort(),
        "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--pids-limit", "256", "--memory", "512m", "--cpus", "1.0", "--network", network,
        "--publish", "0.0.0.0:" + manifest.publicPort() + ":" + manifest.applicationPort(), image), log);
  }

  private String previousImage(String projectId, Consumer<String> log) {
    List<String> ids = output(List.of("docker", "ps", "-aq", "--filter", "label=io.autodeploy.managed=true", "--filter", "label=io.autodeploy.project=" + projectId), log);
    return ids.isEmpty() ? null : first(output(List.of("docker", "inspect", "--format", "{{.Config.Image}}", ids.getFirst()), log));
  }
  private void stopManagedProject(String projectId, Consumer<String> log) {
    for (String id : output(List.of("docker", "ps", "-aq", "--filter", "label=io.autodeploy.managed=true", "--filter", "label=io.autodeploy.project=" + projectId), log)) run(List.of("docker", "rm", "--force", id), log);
  }
  private void restore(String image, String network, AgentBoundary.Manifest manifest, Consumer<String> log) {
    if (image == null || image.isBlank()) return;
    log.accept("Restoring the previous managed image.");
    start("autodeploy-" + manifest.projectId() + "-rollback-" + manifest.deploymentId(), image, network, manifest, log);
  }
  private boolean healthy(AgentBoundary.Manifest manifest, Consumer<String> log) {
    String url = "http://host.docker.internal:" + manifest.publicPort() + manifest.healthPath();
    for (int attempt = 1; attempt <= 30; attempt++) {
      try {
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 200 && response.statusCode() < 400) { log.accept("Health check passed (HTTP " + response.statusCode() + ")."); return true; }
      } catch (Exception ignored) { }
      try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
      if (attempt % 5 == 0) log.accept("Waiting for health check (attempt " + attempt + "/30).");
    }
    return false;
  }

  private boolean run(List<String> command, Consumer<String> log) {
    try { Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); stream(process, log); if (!process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) { process.destroyForcibly(); return false; } return process.exitValue() == 0; }
    catch (IOException e) { log.accept("Agent executable is unavailable."); return false; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
  }
  private List<String> output(List<String> command, Consumer<String> log) {
    List<String> lines = new ArrayList<>(); try { Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line; while ((line = reader.readLine()) != null) { lines.add(line); log.accept(safe(line)); } } process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { } return lines;
  }
  private void stream(Process process, Consumer<String> log) throws IOException { try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line; int sent = 0; while ((line = reader.readLine()) != null && sent++ < 500) log.accept(safe(line)); } }
  private static String safe(String line) { String sanitized = line.replaceAll("(?i)(token|password|secret|authorization)\\s*[=:]\\s*[^\\s]+", "$1=[redacted]"); return sanitized.substring(0, Math.min(sanitized.length(), 900)); }
  private static String first(List<String> lines) { return lines.isEmpty() ? null : lines.getFirst(); }
  private static ExecutionResult failed(String reason) { return new ExecutionResult(false, reason, reason); }
  private static void deleteWorkspace(Path workspace) { if (workspace == null) return; try (var paths = Files.walk(workspace)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { } }
}
