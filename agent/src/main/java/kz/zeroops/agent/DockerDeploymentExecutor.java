package kz.zeroops.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit Docker opt-in: fixed commands, isolated resources and no user shell input. */
@Component
@ConditionalOnProperty(name = "agent.execution-mode", havingValue = "docker")
class DockerDeploymentExecutor implements DeploymentExecutor {
  private static final int FIRST_PUBLIC_PORT = 18100;
  private static final int LAST_PUBLIC_PORT = 18999;
  private final String agentContainerName;
  private final String githubDeployKey;
  private final String githubKnownHosts;
  private final RepositoryScanner scanner;
  private final DockerfileGenerator dockerfiles;
  private final DockerfilePolicy dockerfilePolicy;

  DockerDeploymentExecutor(@Value("${agent.container-name:}") String agentContainerName,
                           @Value("${agent.github-deploy-key:/run/autodeploy/github_deploy_key}") String githubDeployKey,
                           @Value("${agent.github-known-hosts:/tmp/.ssh/known_hosts}") String githubKnownHosts,
                           RepositoryScanner scanner, DockerfileGenerator dockerfiles, DockerfilePolicy dockerfilePolicy) {
    this.agentContainerName = agentContainerName;
    this.githubDeployKey = githubDeployKey;
    this.githubKnownHosts = githubKnownHosts;
    this.scanner = scanner;
    this.dockerfiles = dockerfiles;
    this.dockerfilePolicy = dockerfilePolicy;
  }

  @Override public ExecutionResult execute(AgentBoundary.Manifest manifest, Consumer<String> log) {
    Path workspace = null;
    String network = "autodeploy-net-" + manifest.projectId();
    String name = "autodeploy-" + manifest.projectId() + "-" + manifest.deploymentId();
    String previousImage = null;
    int applicationPort = manifest.applicationPort();
    int publicPort = manifest.publicPort();
    List<DeploymentExecutor.ServicePlan> plan = List.of();
    boolean agentConnected = false;
    try {
      workspace = Files.createTempDirectory("autodeploy-" + manifest.deploymentId() + "-");
      log.accept("Checking out the verified GitHub commit.");
      if (!clone(manifest.repositoryUrl(), manifest.branch(), workspace, log)) return failed("Git checkout failed.");
      if (manifest.commitSha().matches("[A-Fa-f0-9]{7,64}")
          && !run(List.of("git", "-C", workspace.toString(), "checkout", "--detach", manifest.commitSha()), log)) {
        return failed("Requested commit is unavailable.");
      }
      if (manifest.commitSha().startsWith("manual-")) {
        log.accept("No immutable commit was supplied; deploying the checked-out branch head.");
      }
      List<RepositoryScanner.Service> services = scanner.scan(workspace);
      if (services.isEmpty()) return failed("No supported deployable service was found by static analysis.");
      plan = services.stream().map(s -> new DeploymentExecutor.ServicePlan(s.key(), s.path(), s.runtime(), s.port(), s.publicCandidate(), s.hasDockerfile(), s.evidence())).toList();
      RepositoryScanner.Service service = selectService(services, manifest.servicePath());
      if (service == null) return failed("Requested service path is not present in the static deployment plan.", plan);
      log.accept("Static plan selected service " + service.key() + " at " + service.path() + " (" + service.runtime() + ").");
      Path buildContext = service.path().equals(".") ? workspace : workspace.resolve(service.path()).normalize();
      if (!buildContext.startsWith(workspace)) return failed("Unsafe service path was rejected.", plan);
      Path dockerfile = dockerfiles.ensure(buildContext, service.runtime());
      if (!service.hasDockerfile()) log.accept("Generated a reviewed Dockerfile template for " + service.runtime() + "; repository files were not otherwise changed.");
      dockerfilePolicy.verify(dockerfile);
      applicationPort = detectedApplicationPort(dockerfile, service.port(), log);
      if (publicPort == 0) {
        publicPort = nextPublicPort();
        if (publicPort == 0) return failed("No free AutoDeploy public port is available in range 18100-18999.", plan);
        log.accept("Agent selected external port " + publicPort + " from the AutoDeploy range.");
      }
      if (!run(List.of("docker", "network", "inspect", network), log)) {
        log.accept("Creating isolated project network.");
        if (!run(List.of("docker", "network", "create", "--label", "io.autodeploy.managed=true", "--label", "io.autodeploy.project=" + manifest.projectId(), network), log)) return failed("Project network cannot be created.", plan);
      }
      log.accept("Building immutable Docker image.");
      if (!run(List.of("docker", "build", "--pull", "--file", dockerfile.toString(), "--tag", manifest.imageTag(), buildContext.toString()), log)) return failed("Docker image build failed.", plan);
      previousImage = previousImage(manifest.projectId(), log);
      stopManagedProject(manifest.projectId(), log);
      if (!start(name, manifest.imageTag(), network, manifest, applicationPort, publicPort, log)) {
        restore(previousImage, network, manifest, applicationPort, publicPort, log);
        return failed("Docker rejected the isolated container.", plan);
      }
      agentConnected = connectAgent(network, log);
      String privateIp = agentConnected ? containerIp(name, log) : null;
      if (privateIp == null || privateIp.isBlank() || !healthy(privateIp, applicationPort, manifest.healthPath(), log)) {
        run(List.of("docker", "rm", "--force", name), log);
        restore(previousImage, network, manifest, applicationPort, publicPort, log);
        return failed("Health check failed; previous image was restored when available.", plan);
      }
      return new ExecutionResult(true, "Image built, isolated container started and health check passed on port " + publicPort + ".", null, applicationPort, publicPort, service.runtime(), manifest.healthPath(), plan);
    } catch (IOException e) { return new ExecutionResult(false, "Agent workspace is unavailable.", "Agent workspace is unavailable.", null, null, null, null, plan); }
    finally {
      if (agentConnected) run(List.of("docker", "network", "disconnect", network, agentContainerName), log);
      deleteWorkspace(workspace);
    }
  }

  private boolean start(String name, String image, String network, AgentBoundary.Manifest manifest, int applicationPort, int publicPort, Consumer<String> log) {
    return run(List.of("docker", "run", "--detach", "--name", name,
        "--label", "io.autodeploy.managed=true", "--label", "io.autodeploy.project=" + manifest.projectId(),
        "--label", "io.autodeploy.application-port=" + applicationPort, "--label", "io.autodeploy.public-port=" + publicPort,
        "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--pids-limit", "256", "--memory", "512m", "--cpus", "1.0", "--network", network,
        "--publish", "0.0.0.0:" + publicPort + ":" + applicationPort, image), log);
  }

  private String previousImage(String projectId, Consumer<String> log) {
    List<String> ids = output(List.of("docker", "ps", "-aq", "--filter", "label=io.autodeploy.managed=true", "--filter", "label=io.autodeploy.project=" + projectId), log);
    return ids.isEmpty() ? null : first(output(List.of("docker", "inspect", "--format", "{{.Config.Image}}", ids.getFirst()), log));
  }
  private void stopManagedProject(String projectId, Consumer<String> log) {
    for (String id : output(List.of("docker", "ps", "-aq", "--filter", "label=io.autodeploy.managed=true", "--filter", "label=io.autodeploy.project=" + projectId), log)) run(List.of("docker", "rm", "--force", id), log);
  }
  private void restore(String image, String network, AgentBoundary.Manifest manifest, int applicationPort, int publicPort, Consumer<String> log) {
    if (image == null || image.isBlank()) return;
    log.accept("Restoring the previous managed image.");
    start("autodeploy-" + manifest.projectId() + "-rollback-" + manifest.deploymentId(), image, network, manifest, applicationPort, publicPort, log);
  }
  private boolean connectAgent(String network, Consumer<String> log) {
    if (agentContainerName == null || agentContainerName.isBlank()) {
      log.accept("Agent container name is not configured; private health check is unavailable.");
      return false;
    }
    return run(List.of("docker", "network", "connect", network, agentContainerName), log);
  }
  private String containerIp(String containerName, Consumer<String> log) {
    return first(output(List.of("docker", "inspect", "--format", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", containerName), log));
  }
  private boolean healthy(String privateIp, int applicationPort, String healthPath, Consumer<String> log) {
    String url = "http://" + privateIp + ":" + applicationPort + healthPath;
    for (int attempt = 1; attempt <= 30; attempt++) {
      if (probe(url)) { log.accept("Health check passed."); return true; }
      try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
      if (attempt % 5 == 0) log.accept("Waiting for health check (attempt " + attempt + "/30).");
    }
    return false;
  }
  private boolean probe(String url) {
    try {
      Process process = new ProcessBuilder("wget", "-q", "--spider", "--timeout=3", url).start();
      if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
      return process.exitValue() == 0;
    } catch (IOException e) { return false; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
  }

  private boolean clone(String repositoryUrl, String branch, Path workspace, Consumer<String> log) {
    List<String> command = List.of("git", "clone", "--depth", "1", "--branch", branch, repositoryUrl, workspace.toString());
    if (!repositoryUrl.startsWith("git@github.com:")) return run(command, log);
    Path key = Path.of(githubDeployKey);
    if (!Files.isRegularFile(key)) {
      log.accept("SSH repository was requested but the read-only deploy key is unavailable.");
      return false;
    }
    try {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      builder.environment().put("GIT_SSH_COMMAND", "ssh -i " + githubDeployKey + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=" + githubKnownHosts);
      Process process = builder.start(); stream(process, log);
      if (!process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) { process.destroyForcibly(); return false; }
      return process.exitValue() == 0;
    } catch (IOException e) { log.accept("SSH git executable is unavailable."); return false; }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
  }

  /** Static inspection only: the Agent never runs repository scripts to guess configuration. */
  private int detectedApplicationPort(Path dockerfile, int fallback, Consumer<String> log) {
    if (!Files.isRegularFile(dockerfile)) {
      log.accept("No Dockerfile found at repository root; using configured internal port " + fallback + ".");
      return fallback;
    }
    try {
      for (String line : Files.readAllLines(dockerfile)) {
        String normalized = line.trim();
        if (normalized.matches("(?i)^EXPOSE\\s+[0-9]{1,5}(/tcp)?\\s*$")) {
          String digits = normalized.replaceFirst("(?i)^EXPOSE\\s+", "").replaceAll("[^0-9]", "");
          int detected = Integer.parseInt(digits);
          if (detected > 0 && detected < 65536) {
            log.accept("Static Dockerfile analysis detected internal port " + detected + ".");
            return detected;
          }
        }
      }
    } catch (IOException ignored) { log.accept("Dockerfile could not be read; using configured internal port " + fallback + "."); }
    log.accept("Dockerfile exposes no supported port; using configured internal port " + fallback + ".");
    return fallback;
  }

  private RepositoryScanner.Service selectService(List<RepositoryScanner.Service> services, String requestedPath) {
    // A first deployment has no persisted service selection. The manifest's legacy
    // root marker must therefore mean "auto-select", not "build the repository root".
    if (requestedPath != null && !requestedPath.isBlank() && !requestedPath.equals(".")) {
      return services.stream().filter(s -> s.path().equals(requestedPath)).findFirst().orElse(null);
    }
    List<RepositoryScanner.Service> publicServices = services.stream().filter(RepositoryScanner.Service::publicCandidate).toList();
    if (publicServices.size() == 1) return publicServices.getFirst();
    return services.size() == 1 ? services.getFirst() : null;
  }

  private int nextPublicPort() {
    for (int port = FIRST_PUBLIC_PORT; port <= LAST_PUBLIC_PORT; port++) {
      int candidate = port;
      List<String> used = output(List.of("docker", "ps", "--format", "{{.Ports}}"), ignored -> { });
      boolean occupied = used.stream().anyMatch(value -> value.matches(".*[:.]" + candidate + "->.*"));
      if (!occupied) return port;
    }
    return 0;
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
  private static ExecutionResult failed(String reason) { return new ExecutionResult(false, reason, reason, null, null, null, null, List.of()); }
  private static ExecutionResult failed(String reason, List<DeploymentExecutor.ServicePlan> plan) { return new ExecutionResult(false, reason, reason, null, null, null, null, plan); }
  private static void deleteWorkspace(Path workspace) { if (workspace == null) return; try (var paths = Files.walk(workspace)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { } }
}
