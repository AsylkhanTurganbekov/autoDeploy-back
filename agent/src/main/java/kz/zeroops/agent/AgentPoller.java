package kz.zeroops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbound-only transport: verified manifests only, never arbitrary commands. */
@Component
class AgentPoller {
  private final String base; private final String credential; private final long serverId;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json; private final ManifestVerifier verifier; private final DeploymentExecutor executor;
  AgentPoller(@Value("${agent.control-plane-url}") String base, @Value("${agent.credential}") String credential, @Value("${agent.server-id}") long serverId, ObjectMapper json, ManifestVerifier verifier, DeploymentExecutor executor) {
    this.base = base == null ? "" : base.replaceFirst("/+$", ""); this.credential = credential; this.serverId = serverId; this.json = json; this.verifier = verifier; this.executor=executor;
  }
  @Scheduled(fixedDelayString = "${agent.poll-ms}") void poll() {
    if (base.isBlank() || credential.isBlank() || serverId <= 0) return;
    heartbeat();
    try {
      HttpResponse<String> response = http.send(request("/api/v1/agent/" + serverId + "/deployments/next").GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) return;
      Signed signed = json.readValue(response.body(), Signed.class);
      var manifest = verifier.verify(signed.payload(), signed.signature(), serverId);
      if (manifest.isEmpty() || !new AgentBoundary().accepts(manifest.get())) {
        if (manifest.isPresent()) result(manifest.get().deploymentId(), false, "Agent rejected unsafe or expired deployment manifest.", "Manifest validation failed");
        return;
      }
      DeploymentExecutor.ExecutionResult executed=executor.execute(manifest.get()); result(manifest.get().deploymentId(),executed.success(),executed.message(),executed.failureReason());
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      catch (Exception ignored) { /* transient failures are retried without logging secrets */ }
  }
  private void heartbeat() { try { String body = json.writeValueAsString(Map.of("cpuPercent", 0, "ramPercent", 0, "diskPercent", 0, "managedContainers", 0, "agentVersion", "0.1.0")); http.sendAsync(request("/api/v1/agent/" + serverId + "/heartbeat").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { } }
  private void result(String deploymentId, boolean success, String message, String reason) { try { String body = json.writeValueAsString(Map.of("success", success, "message", message, "reason", reason == null ? "" : reason)); http.sendAsync(request("/api/v1/agent/" + serverId + "/deployments/" + deploymentId + "/result").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { } }
  private HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15)).header("X-Agent-Token", credential); }
  record Signed(String payload, String signature) { }
}
