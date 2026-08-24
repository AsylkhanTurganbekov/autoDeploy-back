package kz.zeroops.api;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
  List<Deployment> findAllByOrderByCreatedAtDesc();
  List<Deployment> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
  java.util.Optional<Deployment> findFirstByProjectIdAndStatusAndIdNotOrderByCreatedAtDesc(Long projectId, DeploymentStatus status, Long excludedId);
  List<Deployment> findAllByProjectOwnerIdOrderByCreatedAtDesc(Long ownerId);
  @Query("select d from Deployment d left join fetch d.project p left join fetch p.owner where d.id = :id")
  Optional<Deployment> findWithProjectAndOwnerById(Long id);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Deployment d left join fetch d.project p left join fetch p.owner where d.id = :id")
  Optional<Deployment> findWithProjectAndOwnerByIdForUpdate(Long id);
  @Query("select d from Deployment d join fetch d.project p where p.targetServer.id = :serverId and d.status = kz.zeroops.api.DeploymentStatus.QUEUED order by d.createdAt")
  List<Deployment> findQueuedForServer(Long serverId);
}
