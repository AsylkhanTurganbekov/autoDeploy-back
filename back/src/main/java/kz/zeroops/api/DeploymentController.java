package kz.zeroops.api;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequestMapping("/api/v1/deployments")
public class DeploymentController {
 private final DeploymentRepository deployments; private final DeploymentLogRepository logs; private final CurrentUser current; private final ScheduledExecutorService sse=Executors.newScheduledThreadPool(1);
 public DeploymentController(DeploymentRepository deployments,DeploymentLogRepository logs,CurrentUser current){this.deployments=deployments;this.logs=logs;this.current=current;}
 @GetMapping public List<ProjectController.DeploymentResponse> list(){AppUser u=current.require();return (current.isAdmin(u)?deployments.findAllByOrderByCreatedAtDesc():deployments.findAllByProjectOwnerIdOrderByCreatedAtDesc(u.getId())).stream().map(ProjectController.DeploymentResponse::from).toList();}
 @GetMapping("/{id}") public ProjectController.DeploymentResponse get(@PathVariable Long id){return ProjectController.DeploymentResponse.from(deployment(id));}
 @GetMapping("/{id}/logs") public List<LogResponse> logs(@PathVariable Long id){deployment(id);return logs.findByDeploymentIdOrderByLineNumberAsc(id).stream().map(l->new LogResponse(l.getId(),l.getLineNumber(),l.getMessage(),l.getCreatedAt())).toList();}
 @GetMapping(value="/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter events(@PathVariable Long id){deployment(id);SseEmitter emitter=new SseEmitter(0L);final int[] sent={0};ScheduledFuture<?> future=sse.scheduleAtFixedRate(()-> {try {Deployment d=deployments.findById(id).orElseThrow();List<DeploymentLog> all=logs.findByDeploymentIdOrderByLineNumberAsc(id);for(DeploymentLog log:all)if(log.getLineNumber()>sent[0]){emitter.send(SseEmitter.event().name("log").id(String.valueOf(log.getLineNumber())).data(new LogResponse(log.getId(),log.getLineNumber(),log.getMessage(),log.getCreatedAt())));sent[0]=log.getLineNumber();}emitter.send(SseEmitter.event().name("status").data(ProjectController.DeploymentResponse.from(d)));if(d.getStatus()==DeploymentStatus.SUCCESS||d.getStatus()==DeploymentStatus.FAILED||d.getStatus()==DeploymentStatus.CANCELLED) emitter.complete();}catch(IOException|IllegalStateException ex){emitter.complete();}catch(Exception ex){emitter.completeWithError(ex);}},0,1,TimeUnit.SECONDS);emitter.onCompletion(()->future.cancel(true));emitter.onTimeout(()->future.cancel(true));emitter.onError(x->future.cancel(true));return emitter;}
 private Deployment deployment(Long id){Deployment d=deployments.findWithProjectAndOwnerById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deployment not found"));AppUser u=current.require();if(!current.isAdmin(u)&&(d.getProject()==null||d.getProject().getOwner()==null||!d.getProject().getOwner().getId().equals(u.getId())))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Deployment access denied");return d;}
 public record LogResponse(Long id,int lineNumber,String message,java.time.Instant createdAt){}
}
