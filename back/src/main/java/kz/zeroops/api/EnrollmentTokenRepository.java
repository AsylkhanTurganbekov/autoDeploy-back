package kz.zeroops.api; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface EnrollmentTokenRepository extends JpaRepository<EnrollmentToken,Long>{Optional<EnrollmentToken> findByTokenHash(String hash); List<EnrollmentToken> findAllByServerIdOrderByCreatedAtDesc(Long serverId);}
