package kz.zeroops.api;
import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection,Long> { List<GitHubConnection> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId); Optional<GitHubConnection> findFirstByOwnerIdOrderByCreatedAtDesc(Long ownerId); }
