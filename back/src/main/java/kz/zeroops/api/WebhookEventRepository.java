package kz.zeroops.api; import org.springframework.data.jpa.repository.JpaRepository;
public interface WebhookEventRepository extends JpaRepository<WebhookEvent,Long> { boolean existsByDeliveryId(String deliveryId); }
