package kz.zeroops.api;
import java.time.Instant;
/** Wire contract contains data only; commands and repository Compose are intentionally absent. */
public record DeploymentManifest(int version,Long deploymentId,Long projectId,Long serverId,String repositoryUrl,String branch,String commitSha,ProjectRuntime runtime,int applicationPort,int publicPort,String healthPath,String imageTag,Instant expiresAt,String nonce) {}
