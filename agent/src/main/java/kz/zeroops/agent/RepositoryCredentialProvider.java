package kz.zeroops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Retrieves a GitHub token only for the active, signed deployment and keeps it in memory. */
@Component class RepositoryCredentialProvider {
  private final AgentIdentity identity; private final ObjectMapper json;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  RepositoryCredentialProvider(AgentIdentity identity,ObjectMapper json){this.identity=identity;this.json=json;}
  Optional<String> githubToken(String deploymentId){try{HttpResponse<String> response=http.send(HttpRequest.newBuilder(URI.create(identity.baseUrl()+"/api/v1/agent/"+identity.serverId()+"/deployments/"+deploymentId+"/repository-credential")).timeout(Duration.ofSeconds(15)).header("X-Agent-Token",identity.credential()).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)return Optional.empty();String token=json.readTree(response.body()).path("token").asText();return token.isBlank()?Optional.empty():Optional.of(token);}catch(Exception ignored){return Optional.empty();}}
}
