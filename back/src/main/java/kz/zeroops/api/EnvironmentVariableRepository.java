package kz.zeroops.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, Long> {
  List<EnvironmentVariable> findByProjectIdOrderByCreatedAtAsc(Long projectId);
  Optional<EnvironmentVariable> findByProjectIdAndKey(Long projectId, String key);
}
