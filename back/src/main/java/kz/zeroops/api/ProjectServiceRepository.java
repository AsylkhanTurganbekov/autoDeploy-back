package kz.zeroops.api;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectServiceRepository extends JpaRepository<ProjectService, Long> {
  List<ProjectService> findByProjectIdOrderByCreatedAtAsc(Long projectId);
  Optional<ProjectService> findByIdAndProjectId(Long id, Long projectId);
}
