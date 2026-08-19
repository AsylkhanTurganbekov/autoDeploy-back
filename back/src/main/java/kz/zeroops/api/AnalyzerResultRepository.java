package kz.zeroops.api;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface AnalyzerResultRepository extends JpaRepository<AnalyzerResult,Long> { Optional<AnalyzerResult> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId); }
