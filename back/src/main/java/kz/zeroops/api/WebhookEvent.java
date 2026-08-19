package kz.zeroops.api;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="webhook_events") public class WebhookEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String deliveryId; @Column(nullable=false) private String eventType; private String repositoryFullName; private String action; @Column(nullable=false) private Instant receivedAt=Instant.now(); @Column(nullable=false) private String status;
 protected WebhookEvent(){} public WebhookEvent(String deliveryId,String eventType,String repositoryFullName,String action,String status){this.deliveryId=deliveryId;this.eventType=eventType;this.repositoryFullName=repositoryFullName;this.action=action;this.status=status;}
}
