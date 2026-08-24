package kz.zeroops.api;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Allows an enrolled Agent to report only monotonic, non-terminal execution stages. */
@RestController public class AgentDeploymentStageController {
  private static final Set<DeploymentStatus> ALLOWED=Set.of(DeploymentStatus.CHECKOUT,DeploymentStatus.ANALYZING,DeploymentStatus.BUILDING,DeploymentStatus.DEPLOYING,DeploymentStatus.HEALTH_CHECKING);
  private final ManagedServerRepository servers; private final DeploymentRepository deployments;
  public AgentDeploymentStageController(ManagedServerRepository servers,DeploymentRepository deployments){this.servers=servers;this.deployments=deployments;}
  @PostMapping("/api/v1/agent/{serverId}/deployments/{deploymentId}/stage") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void stage(@PathVariable Long serverId,@PathVariable Long deploymentId,@RequestHeader("X-Agent-Token") String credential,@RequestBody Stage request){
    ManagedServer server=servers.findById(serverId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Server not found"));
    if(server.getAgentTokenHash()==null||!java.security.MessageDigest.isEqual(server.getAgentTokenHash().getBytes(),ServerController.hash(credential).getBytes()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Agent credential is invalid");
    Deployment deployment=deployments.findWithProjectAndOwnerById(deploymentId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deployment not found"));
    if(deployment.getProject()==null||deployment.getProject().getTargetServer()==null||!deployment.getProject().getTargetServer().getId().equals(serverId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Deployment belongs to another server");
    DeploymentStatus next; try{next=DeploymentStatus.valueOf(request.status());}catch(Exception bad){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Unsupported deployment stage");}
    if(!ALLOWED.contains(next)||deployment.getStatus()==DeploymentStatus.SUCCESS||deployment.getStatus()==DeploymentStatus.FAILED||deployment.getStatus()==DeploymentStatus.ROLLED_BACK||rank(next)<rank(deployment.getStatus()))return;
    deployment.moveTo(next); deployments.save(deployment);
  }
  private int rank(DeploymentStatus status){return switch(status){case CHECKOUT->1;case ANALYZING->2;case BUILDING->3;case DEPLOYING->4;case HEALTH_CHECKING->5;default->0;};}
  public record Stage(String status){}
}
