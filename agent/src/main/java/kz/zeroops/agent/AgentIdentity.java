package kz.zeroops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stores only the post-enrollment Agent identity with owner-only permissions. */
@Component
class AgentIdentity {
  private final String controlPlaneUrl, enrollmentToken;
  private final long configuredServerId;
  private final String configuredCredential;
  private final Path identityPath;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private volatile long serverId;
  private volatile String credential = "";

  AgentIdentity(
      @Value("${agent.control-plane-url}") String controlPlaneUrl,
      @Value("${agent.server-id}") long configuredServerId,
      @Value("${agent.credential}") String configuredCredential,
      @Value("${agent.enrollment-token:}") String enrollmentToken,
      @Value("${agent.identity-path:/var/lib/autodeploy-agent/identity.json}") String identityPath,
      ObjectMapper json) {
    this.controlPlaneUrl = controlPlaneUrl == null ? "" : controlPlaneUrl.replaceFirst("/+$", "");
    this.configuredServerId = configuredServerId;
    this.configuredCredential = configuredCredential == null ? "" : configuredCredential;
    this.enrollmentToken = enrollmentToken == null ? "" : enrollmentToken;
    this.identityPath = Path.of(identityPath);
    this.json = json;
  }

  @PostConstruct void initialize() { ensureRegistered(); }

  synchronized boolean ensureRegistered() {
    if (ready()) return true;
    if (loadPersisted()) return true;
    if (configuredServerId > 0 && !configuredCredential.isBlank()) {
      serverId = configuredServerId;
      credential = configuredCredential;
      return true;
    }
    if (controlPlaneUrl.isBlank() || enrollmentToken.isBlank()) return false;
    try {
      String body = json.writeValueAsString(new EnrollmentRequest(enrollmentToken));
      HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(controlPlaneUrl + "/api/v1/agent/enroll"))
          .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) return false;
      EnrollmentResponse enrolled = json.readValue(response.body(), EnrollmentResponse.class);
      if (enrolled.serverId() == null || enrolled.serverId() <= 0 || enrolled.credential() == null || enrolled.credential().isBlank()) return false;
      serverId = enrolled.serverId();
      credential = enrolled.credential();
      persist();
      return true;
    } catch (Exception ignored) { return false; }
  }

  boolean ready() { return serverId > 0 && !credential.isBlank(); }
  String baseUrl() { return controlPlaneUrl; }
  long serverId() { return serverId; }
  String credential() { return credential; }

  private boolean loadPersisted() {
    if (!Files.isRegularFile(identityPath)) return false;
    try {
      StoredIdentity stored = json.readValue(Files.readString(identityPath), StoredIdentity.class);
      if (stored.serverId() == null || stored.serverId() <= 0 || stored.credential() == null || stored.credential().isBlank()) return false;
      serverId = stored.serverId(); credential = stored.credential(); return true;
    } catch (Exception ignored) { return false; }
  }

  private void persist() throws IOException {
    Files.createDirectories(identityPath.getParent());
    setOwnerOnly(identityPath.getParent());
    Path temporary = Files.createTempFile(identityPath.getParent(), "identity-", ".tmp");
    try {
      Files.writeString(temporary, json.writeValueAsString(new StoredIdentity(serverId, credential)), StandardCharsets.UTF_8);
      setOwnerOnly(temporary);
      try { Files.move(temporary, identityPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
      catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, identityPath, StandardCopyOption.REPLACE_EXISTING); }
      setOwnerOnly(identityPath);
    } finally { Files.deleteIfExists(temporary); }
  }

  private static void setOwnerOnly(Path path) {
    try { Files.setPosixFilePermissions(path, Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE, java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)); }
    catch (Exception ignored) { }
  }
  private record EnrollmentRequest(String token) { }
  private record EnrollmentResponse(Long serverId, String credential) { }
  private record StoredIdentity(Long serverId, String credential) { }
}
