package kz.zeroops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbound-only transport: verified manifests only, never arbitrary commands. */
@Component
class AgentPoller {
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json; private final ManifestVerifier verifier; private final DeploymentExecutor executor;
  private final AgentIdentity identity;
  AgentPoller(AgentIdentity identity, ObjectMapper json, ManifestVerifier verifier, DeploymentExecutor executor) {
    this.identity = identity; this.json = json; this.verifier = verifier; this.executor=executor;
  }
  @Scheduled(fixedDelayString = "${agent.poll-ms}") void poll() {
    if (!identity.ensureRegistered()) return;
    long serverId = identity.serverId();
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
      DeploymentExecutor.ExecutionResult executed=executor.execute(manifest.get(),line->log(manifest.get().deploymentId(),line)); log(manifest.get().deploymentId(),executed.message()); result(manifest.get().deploymentId(),executed);
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      catch (Exception ignored) { /* transient failures are retried without logging secrets */ }
  }
  private void heartbeat() { try { String body = json.writeValueAsString(Map.of("cpuPercent", 0, "ramPercent", 0, "diskPercent", 0, "managedContainers", 0, "agentVersion", "0.1.0")); http.sendAsync(request("/api/v1/agent/" + identity.serverId() + "/heartbeat").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { } }
  private void result(String deploymentId, DeploymentExecutor.ExecutionResult value) { try { Map<String,Object> body=new java.util.HashMap<>();body.put("success",value.success());body.put("message",value.message());body.put("reason",value.failureReason()==null?"":value.failureReason());if(value.success()&&value.applicationPort()!=null&&value.publicPort()!=null){body.put("applicationPort",value.applicationPort());body.put("publicPort",value.publicPort());body.put("runtime",value.runtime());body.put("healthPath",value.healthPath());}http.sendAsync(request("/api/v1/agent/" + identity.serverId() + "/deployments/" + deploymentId + "/result").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { } }
  private void log(String deploymentId,String message) { try { String body=json.writeValueAsString(Map.of("messages",List.of(message))); http.sendAsync(request("/api/v1/agent/"+identity.serverId()+"/deployments/"+deploymentId+"/logs").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.discarding()); } catch(Exception ignored){} }
  private HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create(identity.baseUrl() + path)).timeout(Duration.ofSeconds(15)).header("X-Agent-Token", identity.credential()); }
  record Signed(String payload, String signature) { }
}
