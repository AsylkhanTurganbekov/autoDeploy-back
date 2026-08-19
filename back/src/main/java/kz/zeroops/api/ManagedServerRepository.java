package kz.zeroops.api; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedServerRepository extends JpaRepository<ManagedServer,Long>{List<ManagedServer> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);}
