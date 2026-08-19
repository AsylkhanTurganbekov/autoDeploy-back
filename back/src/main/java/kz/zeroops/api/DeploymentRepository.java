package kz.zeroops.api;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
  List<Deployment> findAllByOrderByCreatedAtDesc();
  List<Deployment> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
  List<Deployment> findAllByProjectOwnerIdOrderByCreatedAtDesc(Long ownerId);
  @Query("select d from Deployment d left join fetch d.project p left join fetch p.owner where d.id = :id")
  Optional<Deployment> findWithProjectAndOwnerById(Long id);
}
