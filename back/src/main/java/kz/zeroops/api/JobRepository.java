package kz.zeroops.api;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface JobRepository extends JpaRepository<Job,Long> { List<Job> findAllByProjectIdOrderByCreatedAtDesc(Long projectId); Optional<Job> findByDedupeKey(String dedupeKey); }
