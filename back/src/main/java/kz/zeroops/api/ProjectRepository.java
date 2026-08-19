package kz.zeroops.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  List<Project> findAllByOrderByCreatedAtDesc();
  List<Project> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
  Optional<Project> findBySlug(String slug);
  boolean existsBySlug(String slug);
}
