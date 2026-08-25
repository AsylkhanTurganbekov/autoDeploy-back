package kz.zeroops.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
  private final String workspaceRoot;
  private final String githubDeployKey;
  private final String githubKnownHosts;
  private final RepositoryScanner scanner;
  private final DockerfileGenerator dockerfiles;
  private final DockerfilePolicy dockerfilePolicy;
  private final RepositoryCredentialProvider credentials;
  private final ObjectMapper json = new ObjectMapper();

  DockerDeploymentExecutor(@Value("${agent.container-name:}") String agentContainerName,
                           @Value("${agent.workspace-root:/var/lib/autodeploy-agent/workspaces}") String workspaceRoot,
                           @Value("${agent.github-deploy-key:/tmp/.ssh/github_deploy_key}") String githubDeployKey,
                           @Value("${agent.github-known-hosts:/run/autodeploy/github_known_hosts}") String githubKnownHosts,
                           RepositoryScanner scanner, DockerfileGenerator dockerfiles, DockerfilePolicy dockerfilePolicy, RepositoryCredentialProvider credentials) {
    this.agentContainerName = agentContainerName;
    this.workspaceRoot = workspaceRoot;
    this.githubDeployKey = githubDeployKey;
    this.githubKnownHosts = githubKnownHosts;
    this.scanner = scanner;
    this.dockerfiles = dockerfiles;
    this.dockerfilePolicy = dockerfilePolicy;
    this.credentials = credentials;
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
      String preflightFailure = preflight(log);
      if (preflightFailure != null) return failed(preflightFailure);
      Path root = Path.of(workspaceRoot);
      Files.createDirectories(root);
      workspace = Files.createTempDirectory(root, "autodeploy-" + manifest.deploymentId() + "-");
      log.accept("Checking out the verified GitHub commit.");
      if (!clone(manifest.repositoryUrl(), manifest.branch(), workspace, credentials.githubToken(manifest.deploymentId()).orElse(null), log)) return failed("Git checkout failed.");
      if (manifest.commitSha().matches("[A-Fa-f0-9]{7,64}")
          && !run(List.of("git", "-C", workspace.toString(), "checkout", "--detach", manifest.commitSha()), log)) {
        return failed("Requested commit is unavailable.");
      }
      if (manifest.commitSha().startsWith("manual-")) {
        log.accept("No immutable commit was supplied; deploying the checked-out branch head.");
      }
      Path composeFile = findComposeFile(workspace);
      if (composeFile != null) return deployCompose(manifest, workspace, composeFile, log);
      List<RepositoryScanner.Service> services = scanner.scan(workspace);
      if (services.isEmpty()) return failed("No supported deployable service was found by static analysis.");
      plan = services.stream().map(s -> new DeploymentExecutor.ServicePlan(s.key(), s.path(), s.runtime(), s.port(), s.publicCandidate(), s.hasDockerfile(), s.evidence())).toList();
      RepositoryScanner.Service service = selectService(services, manifest.servicePath());
      if (service == null) return failed("Requested service path is not present in the static deployment plan.", plan);
      log.accept("Static plan selected service " + service.key() + " at " + service.path() + " (" + service.runtime() + ").");
      Path buildContext = service.path().equals(".") ? workspace : workspace.resolve(service.path()).normalize();
      if (!buildContext.startsWith(workspace)) return failed("Unsafe service path was rejected.", plan);
      log.accept("Deployment method selected: " + (service.hasDockerfile() ? "DOCKERFILE" : "GENERATED_DOCKERFILE") + ".");
      Path dockerfile = dockerfiles.ensure(buildContext, service.runtime());
      if (!service.hasDockerfile()) log.accept("Generated a reviewed Dockerfile template for " + service.runtime() + "; repository files were not otherwise changed.");
      dockerfilePolicy.verify(dockerfile);
      applicationPort = detectedApplicationPort(dockerfile, service.port(), log);
      if (publicPort == 0) {
        publicPort = nextPublicPort();
        if (publicPort == 0) return failed("No free AutoDeploy public port is available in range 18100-18999.", plan);
        log.accept("Agent selected external port " + publicPort + " from the AutoDeploy range.");
      }
      log.accept("Container ports selected: internal " + applicationPort + ", external " + publicPort + ".");
      if (!run(List.of("docker", "network", "inspect", network), log)) {
        log.accept("Creating isolated project network.");
        if (!run(List.of("docker", "network", "create", "--label", "io.autodeploy.managed=true", "--label", "io.autodeploy.project=" + manifest.projectId(), network), log)) return failed("Project network cannot be created.", plan);
      }
      log.accept("Building immutable Docker image.");
      if (!run(List.of("docker", "build", "--pull", "--file", dockerfile.toString(), "--tag", manifest.imageTag(), buildContext.toString()), log)) return failed("Docker image build failed.", plan);
      previousImage = previousImage(manifest.projectId(), log);
      stopManagedProject(manifest.projectId(), log);
      log.accept("Starting isolated container.");
      if (!start(name, manifest.imageTag(), network, manifest, applicationPort, publicPort, log)) {
        restore(previousImage, network, manifest, applicationPort, publicPort, log);
        return failed("Docker rejected the isolated container.", plan);
      }
      agentConnected = connectAgent(network, log);
      String privateIp = agentConnected ? containerIp(name, log) : null;
      log.accept("Starting health check.");
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

  /** Compose is preferred when present: it preserves the repository's multi-service topology. */
  private ExecutionResult deployCompose(AgentBoundary.Manifest manifest, Path workspace, Path composeFile, Consumer<String> log) throws IOException {
    String project = "autodeploy-" + manifest.projectId() + "-" + manifest.deploymentId();
    Path sanitized = workspace.resolve(".autodeploy-compose.yml");
    Path override = workspace.resolve(".autodeploy-compose.override.yml");
    log.accept("Deployment method selected: COMPOSE.");
    log.accept("Compose file selected: " + composeFile.getFileName() + ".");
    sanitizeCompose(composeFile, sanitized);
    ComposeService primary = primaryComposeService(project, sanitized);
    if (primary == null) return failed("Compose stack has no unprofiled HTTP service with exactly one published port.");
    int publicPort = manifest.publicPort() == 0 ? nextPublicPort() : manifest.publicPort();
    if (publicPort == 0) return failed("No free AutoDeploy public port is available in range 18100-18999.");
    writeComposeOverride(override, primary.name(), primary.internalPort(), publicPort, manifest);
    log.accept("Compose service selected: " + primary.name() + "; internal port " + primary.internalPort() + ", external port " + publicPort + ".");
    List<String> base = List.of("docker", "compose", "-p", project, "-f", sanitized.toString(), "-f", override.toString());
    List<String> up = new ArrayList<>(base); up.addAll(List.of("up", "-d", "--build"));
    log.accept("Starting isolated Compose stack.");
    if (!run(up, log)) { composeLogs(base, log); composeDown(base, log); return failed("Docker Compose could not start the current deployment."); }
    List<String> ids = composeOutput(base, List.of("ps", "-aq"));
    if (ids.isEmpty()) { composeLogs(base, log); composeDown(base, log); return failed("Docker Compose started no containers."); }
    String network = project + "_default";
    boolean connected = connectAgent(network, log);
    try {
      String primaryId = first(composeOutput(base, List.of("ps", "-q", primary.name())));
      String privateIp = primaryId == null ? null : composeContainerIp(primaryId, network);
      log.accept("Starting health check for Compose service " + primary.name() + " on " + manifest.healthPath() + ".");
      if (!connected || privateIp == null || privateIp.isBlank() || !healthy(privateIp, primary.internalPort(), manifest.healthPath(), log)) {
        composeLogs(base, log); composeDown(base, log);
        return failed("Compose health check failed; only the current Compose deployment was stopped.");
      }
      stopManagedProjectExcept(manifest.projectId(), ids, log);
      List<DeploymentExecutor.ServicePlan> plan = List.of(new DeploymentExecutor.ServicePlan(primary.name(), ".", "DOCKERFILE", primary.internalPort(), true, true, List.of("Compose: " + composeFile.getFileName())));
      return new ExecutionResult(true, "Compose stack started and health check passed on port " + publicPort + ".", null, primary.internalPort(), publicPort, "DOCKERFILE", manifest.healthPath(), plan);
    } finally { if (connected) run(List.of("docker", "network", "disconnect", network, agentContainerName), log); }
  }

  private Path findComposeFile(Path root) {
    for (String name : List.of("docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml")) {
      Path candidate = root.resolve(name);
      if (Files.isRegularFile(candidate)) return candidate;
    }
    return null;
  }

  private void sanitizeCompose(Path source, Path target) throws IOException {
    List<String> kept = new ArrayList<>();
    for (String line : Files.readAllLines(source)) {
      String normalized = line.trim().toLowerCase();
      if (normalized.matches("container_name\\s*:.*")) continue;
      if (normalized.contains("/var/run/docker.sock") || normalized.matches("privileged\\s*:\\s*true") || normalized.matches("network_mode\\s*:\\s*host") || normalized.matches("pid\\s*:\\s*host") || normalized.matches("ipc\\s*:\\s*host") || normalized.startsWith("cap_add:") || normalized.startsWith("security_opt:") || normalized.startsWith("devices:")) throw new IOException("Compose file violates the AutoDeploy isolation policy.");
      kept.add(line);
    }
    Files.write(target, kept);
  }

  private ComposeService primaryComposeService(String project, Path compose) {
    List<String> command = List.of("docker", "compose", "-p", project, "-f", compose.toString(), "config", "--format", "json");
    try {
      List<String> result = outputQuiet(command);
      JsonNode services = json.readTree(String.join("\n", result)).path("services");
      List<ComposeService> candidates = new ArrayList<>();
      java.util.Iterator<Map.Entry<String, JsonNode>> iterator = services.fields();
      while (iterator.hasNext()) {
        Map.Entry<String, JsonNode> entry = iterator.next();
        if (!entry.getKey().matches("[A-Za-z0-9_.-]{1,100}") || entry.getValue().has("profiles")) continue;
        JsonNode ports = entry.getValue().path("ports");
        if (!ports.isArray() || ports.size() != 1) continue;
        int target = ports.get(0).path("target").asInt(0);
        if (target > 0 && target < 65536) candidates.add(new ComposeService(entry.getKey(), target));
      }
      return candidates.size() == 1 ? candidates.getFirst() : null;
    } catch (Exception ignored) { return null; }
  }

  private void writeComposeOverride(Path target, String service, int internalPort, int publicPort, AgentBoundary.Manifest manifest) throws IOException {
    String yaml = "services:\n  " + service + ":\n    ports:\n      - target: " + internalPort + "\n        published: \"" + publicPort + "\"\n        host_ip: 0.0.0.0\n        protocol: tcp\n    labels:\n      io.autodeploy.managed: \"true\"\n      io.autodeploy.project: \"" + manifest.projectId() + "\"\n      io.autodeploy.deployment: \"" + manifest.deploymentId() + "\"\n";
    Files.writeString(target, yaml);
  }

  private List<String> composeOutput(List<String> base, List<String> tail) { List<String> command = new ArrayList<>(base); command.addAll(tail); return outputQuiet(command); }
  private void composeDown(List<String> base, Consumer<String> log) { List<String> command = new ArrayList<>(base); command.addAll(List.of("down", "--remove-orphans")); run(command, log); }
  private void composeLogs(List<String> base, Consumer<String> log) { log.accept("Docker Compose logs for the current deployment:"); List<String> command = new ArrayList<>(base); command.addAll(List.of("logs", "--no-color", "--tail", "200")); run(command, log); }
  private String composeContainerIp(String id, String network) { return first(outputQuiet(List.of("docker", "inspect", "--format", "{{with index .NetworkSettings.Networks \"" + network + "\"}}{{.IPAddress}}{{end}}", id))); }
  private void stopManagedProjectExcept(String projectId, List<String> keep, Consumer<String> log) { for (String id : output(List.of("docker", "ps", "-aq", "--filter", "label=io.autodeploy.managed=true", "--filter", "label=io.autodeploy.project=" + projectId), log)) if (!keep.contains(id)) run(List.of("docker", "rm", "--force", id), log); }
  private record ComposeService(String name, int internalPort) { }

  /** Checks only the Agent's own prerequisites before an untrusted repository is read. */
  private String preflight(Consumer<String> log) {
    log.accept("Preflight: checking Docker daemon, socket access, disk space and AutoDeploy port range.");
    if (!run(List.of("docker", "version", "--format", "{{.Server.Version}}"), log))
      return "Agent cannot access the Docker daemon. Check the AutoDeploy Agent Docker socket group.";
    if (!run(List.of("docker", "info", "--format", "{{.Driver}}"), log))
      return "Docker daemon is unavailable for the AutoDeploy Agent.";
    try { Files.createDirectories(Path.of(workspaceRoot)); }
    catch (IOException unavailable) { return "Agent workspace volume is unavailable or not writable."; }
    List<String> disk = output(List.of("df", "-Pk", workspaceRoot), ignored -> { });
    if (disk.size() > 1) {
      String[] fields = disk.get(1).trim().split("\\s+");
      if (fields.length >= 4) try {
        long availableKb = Long.parseLong(fields[3]);
        if (availableKb < 1_048_576L) return "Agent workspace has less than 1 GiB free; free space before deployment.";
      } catch (NumberFormatException ignored) { log.accept("Preflight: disk capacity could not be parsed."); }
    }
    if (nextPublicPort() == 0) return "No free AutoDeploy public port is available in range 18100-18999.";
    log.accept("Preflight passed.");
    return null;
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

  private boolean clone(String repositoryUrl, String branch, Path workspace, String githubToken, Consumer<String> log) {
    List<String> command = List.of("git", "clone", "--depth", "1", "--branch", branch, repositoryUrl, workspace.toString());
    if (repositoryUrl.startsWith("https://github.com/") && githubToken != null) return cloneWithToken(command, workspace, githubToken, log);
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

  private boolean cloneWithToken(List<String> command, Path workspace, String token, Consumer<String> log) {
    Path askPass = null;
    try {
      askPass = Files.createTempFile(workspace.getParent(), "github-askpass-", ".sh");
      Files.writeString(askPass, "#!/bin/sh\ncase \"$1\" in *Username*) printf '%s\\n' x-access-token;; *) printf '%s\\n' \"$AUTODEPLOY_GITHUB_TOKEN\";; esac\n");
      Files.setPosixFilePermissions(askPass, java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
      ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true);
      builder.environment().put("GIT_ASKPASS",askPass.toString()); builder.environment().put("GIT_TERMINAL_PROMPT","0"); builder.environment().put("AUTODEPLOY_GITHUB_TOKEN",token);
      Process process=builder.start(); stream(process,log); if(!process.waitFor(10,java.util.concurrent.TimeUnit.MINUTES)){process.destroyForcibly();return false;} return process.exitValue()==0;
    } catch (Exception e) { log.accept("GitHub HTTPS credential helper is unavailable."); return false; }
    finally { if(askPass!=null)try{Files.deleteIfExists(askPass);}catch(IOException ignored){} }
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
    if (requestedPath != null && !requestedPath.isBlank()) return services.stream().filter(s -> s.path().equals(requestedPath)).findFirst().orElse(null);
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
  /** Compose config can contain resolved environment values, so it is intentionally never streamed to user logs. */
  private List<String> outputQuiet(List<String> command) {
    List<String> lines = new ArrayList<>();
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line; while ((line = reader.readLine()) != null) lines.add(line); }
      if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) return List.of();
    } catch (Exception ignored) { return List.of(); }
    return lines;
  }
  private void stream(Process process, Consumer<String> log) throws IOException { try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line; int sent = 0; while ((line = reader.readLine()) != null && sent++ < 500) log.accept(safe(line)); } }
  private static String safe(String line) { String sanitized = line.replaceAll("(?i)(token|password|secret|authorization)\\s*[=:]\\s*[^\\s]+", "$1=[redacted]"); return sanitized.substring(0, Math.min(sanitized.length(), 900)); }
  private static String first(List<String> lines) { return lines.isEmpty() ? null : lines.getFirst(); }
  private static ExecutionResult failed(String reason) { return new ExecutionResult(false, reason, reason, null, null, null, null, List.of()); }
  private static ExecutionResult failed(String reason, List<DeploymentExecutor.ServicePlan> plan) { return new ExecutionResult(false, reason, reason, null, null, null, null, plan); }
  private static void deleteWorkspace(Path workspace) { if (workspace == null) return; try (var paths = Files.walk(workspace)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { } }
}
