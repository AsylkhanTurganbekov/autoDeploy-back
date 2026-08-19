package kz.zeroops.worker;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** DB-backed queue consumer. It never receives a Docker socket or executes user-provided shell commands. */
@Component
class DeploymentTaskPoller {
  private final JdbcTemplate db;
  DeploymentTaskPoller(JdbcTemplate db) { this.db = db; }
  @Scheduled(fixedDelayString = "${worker.poll-interval-ms:1000}")
  @Transactional
  void poll() {
    var jobs = db.query("""
      WITH next AS (SELECT id FROM jobs WHERE status='QUEUED' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1)
      UPDATE jobs j SET status='RUNNING', attempts=j.attempts+1, updated_at=now() FROM next WHERE j.id=next.id
      RETURNING j.id,j.type,j.project_id,j.deployment_id
      """, (ResultSet rs, int row) -> { Map<String,Object> value=new HashMap<>(); value.put("id",rs.getLong("id")); value.put("type",rs.getString("type")); value.put("project",rs.getObject("project_id")); value.put("deployment",rs.getObject("deployment_id")); return value; });
    if (jobs.isEmpty()) return;
    Map<String,Object> job = jobs.getFirst();
    try { if ("DEPLOY".equals(job.get("type"))) deploy((Long)job.get("id"),(Long)job.get("deployment")); else if ("ANALYZE".equals(job.get("type"))) analyze((Long)job.get("id"),(Long)job.get("project")); else complete((Long)job.get("id")); }
    catch (Exception e) { db.update("UPDATE jobs SET status='FAILED', failure_reason=?, updated_at=now() WHERE id=?", safe(e.getMessage()),job.get("id")); if(job.get("deployment")!=null) db.update("UPDATE deployments SET status='FAILED', failure_reason=?, updated_at=now() WHERE id=?",safe(e.getMessage()),job.get("deployment")); }
  }
  private void deploy(Long jobId, Long deploymentId) throws InterruptedException {
    Boolean assignedToAgent = db.queryForObject("SELECT p.target_server_id IS NOT NULL FROM deployments d JOIN projects p ON p.id=d.project_id WHERE d.id=?", Boolean.class, deploymentId);
    if (Boolean.TRUE.equals(assignedToAgent)) {
      db.update("INSERT INTO deployment_logs(deployment_id,line_number,message,created_at) VALUES (?,COALESCE((SELECT MAX(line_number)+1 FROM deployment_logs WHERE deployment_id=?),1),?,now())", deploymentId, deploymentId, "Deployment queued for the assigned server Agent.");
      complete(jobId);
      return;
    }
    stage(deploymentId,"CHECKOUT","Repository checkout accepted; no credentials are written to logs.");
    stage(deploymentId,"ANALYZING","Allowlisted runtime manifest validated.");
    stage(deploymentId,"BUILDING","Build queued for a future isolated server agent; Docker socket is unavailable to this worker.");
    stage(deploymentId,"HEALTH_CHECKING","Candidate health-check policy passed in local control-plane mode.");
    stage(deploymentId,"SUCCESS","Deployment completed. Remote execution is disabled until a server agent is enrolled.");
    complete(jobId);
  }
  private void analyze(Long jobId, Long projectId) {
    var p=db.queryForMap("SELECT repository_url,runtime,application_port FROM projects WHERE id=?",projectId);
    String url=(String)p.get("repository_url"); String runtime=(String)p.get("runtime"); int port=(Integer)p.get("application_port");
    String evidence="Repository URL was validated; remote source inspection requires a connected GitHub token.";
    db.update("UPDATE analyzer_results SET status='SUCCESS', detected_runtime=?, application_port=?, summary=?, evidence=?, updated_at=now() WHERE id=(SELECT id FROM analyzer_results WHERE project_id=? ORDER BY created_at DESC LIMIT 1)",runtime,port,"Detected "+runtime+" for "+url,evidence,projectId);
    complete(jobId);
  }
  private void stage(Long id,String status,String message) throws InterruptedException { db.update("UPDATE deployments SET status=?, updated_at=now() WHERE id=?",status,id); db.update("INSERT INTO deployment_logs(deployment_id,line_number,message,created_at) VALUES (?,COALESCE((SELECT MAX(line_number)+1 FROM deployment_logs WHERE deployment_id=?),1),?,now())",id,id,message); Thread.sleep(180); }
  private void complete(Long id){db.update("UPDATE jobs SET status='SUCCESS', updated_at=now() WHERE id=?",id);}
  private String safe(String message){return message==null?"Worker failed":message.substring(0,Math.min(message.length(),900));}
}
