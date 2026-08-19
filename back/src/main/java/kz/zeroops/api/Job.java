package kz.zeroops.api;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="jobs")
public class Job {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private JobType type;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private JobStatus status=JobStatus.QUEUED;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="deployment_id") private Deployment deployment;
 @Column(nullable=false,columnDefinition="TEXT") private String payload;
 @Column(nullable=false) private int attempts;
 @Column(nullable=false) private int maxAttempts=3;
 @Column(length=1000) private String failureReason;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 @Column(nullable=false) private Instant updatedAt=Instant.now();
 @Column(name="dedupe_key",length=255) private String dedupeKey;
 protected Job(){}
 public Job(JobType type,Project project,Deployment deployment,String payload,String dedupeKey){this.type=type;this.project=project;this.deployment=deployment;this.payload=payload;this.dedupeKey=dedupeKey;}
 public Long getId(){return id;} public JobType getType(){return type;} public JobStatus getStatus(){return status;} public Project getProject(){return project;} public Deployment getDeployment(){return deployment;} public String getPayload(){return payload;} public int getAttempts(){return attempts;} public String getFailureReason(){return failureReason;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public void start(){status=JobStatus.RUNNING;attempts++;updatedAt=Instant.now();} public void success(){status=JobStatus.SUCCESS;updatedAt=Instant.now();} public void fail(String message){status=JobStatus.FAILED;failureReason=message;updatedAt=Instant.now();}
}
