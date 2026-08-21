package kz.zeroops.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {
  List<DeploymentLog> findByDeploymentIdOrderByLineNumberAsc(Long deploymentId);

  @Query("select coalesce(max(log.lineNumber), 0) from DeploymentLog log where log.deployment.id = :deploymentId")
  int maxLineNumber(@Param("deploymentId") Long deploymentId);
}
