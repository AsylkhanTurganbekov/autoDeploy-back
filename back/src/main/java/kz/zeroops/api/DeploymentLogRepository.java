package kz.zeroops.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {
  List<DeploymentLog> findByDeploymentIdOrderByLineNumberAsc(Long deploymentId);
}
