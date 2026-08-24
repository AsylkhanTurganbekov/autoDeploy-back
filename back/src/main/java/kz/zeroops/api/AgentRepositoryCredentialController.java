package kz.zeroops.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Delivers an owner's encrypted GitHub PAT only to the enrolled target Agent.
 * The credential is never included in manifests, logs, command arguments or persisted by the Agent. */
@RestController
public class AgentRepositoryCredentialController {
  private final ManagedServerRepository servers; private final DeploymentRepository deployments;
  private final GitHubConnectionRepository connections; private final SecretCodec secrets;
  public AgentRepositoryCredentialController(ManagedServerRepository servers,DeploymentRepository deployments,GitHubConnectionRepository connections,SecretCodec secrets){this.servers=servers;this.deployments=deployments;this.connections=connections;this.secrets=secrets;}
  @GetMapping("/api/v1/agent/{serverId}/deployments/{deploymentId}/repository-credential")
  public ResponseEntity<Credential> credential(@PathVariable Long serverId,@PathVariable Long deploymentId,@RequestHeader("X-Agent-Token") String value){
    ManagedServer server=servers.findById(serverId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Server not found"));
    if(server.getAgentTokenHash()==null||!java.security.MessageDigest.isEqual(server.getAgentTokenHash().getBytes(),ServerController.hash(value).getBytes()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Agent credential is invalid");
    Deployment deployment=deployments.findWithProjectAndOwnerById(deploymentId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deployment not found"));
    Project project=deployment.getProject();
    if(project==null||project.getTargetServer()==null||!project.getTargetServer().getId().equals(serverId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Deployment belongs to another server");
    if(!project.getRepositoryUrl().startsWith("https://github.com/"))return ResponseEntity.noContent().build();
    return connections.findFirstByOwnerIdOrderByCreatedAtDesc(project.getOwner().getId()).map(connection->ResponseEntity.ok(new Credential(secrets.decrypt(connection.getTokenCiphertext())))).orElseGet(()->ResponseEntity.noContent().build());
  }
  public record Credential(String token){}
}
