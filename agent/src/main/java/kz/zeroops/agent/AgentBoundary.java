package kz.zeroops.agent;
import java.time.Instant; import java.util.Set; import org.springframework.stereotype.Component;
/** Rejects any execution input outside the signed-manifest allowlist. */
@Component public class AgentBoundary { private static final Set<String> RUNTIMES=Set.of("NODE","SPRING_BOOT","DOCKERFILE"); public boolean accepts(Manifest m){return m!=null&&RUNTIMES.contains(m.runtime())&&m.expiresAt().isAfter(Instant.now())&&m.commitSha().matches("[A-Fa-f0-9]{7,64}")&&m.applicationPort()>0&&m.applicationPort()<65536&&m.imageTag().matches("[a-z0-9._/-]+:[a-f0-9]{7,64}")&&!m.rawCommand();} public record Manifest(String deploymentId,String projectId,String serverId,String commitSha,String runtime,int applicationPort,String imageTag,Instant expiresAt,boolean rawCommand){} }
