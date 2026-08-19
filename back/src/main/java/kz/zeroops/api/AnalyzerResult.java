package kz.zeroops.api;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="analyzer_results")
public class AnalyzerResult {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private JobStatus status=JobStatus.QUEUED;
 @Enumerated(EnumType.STRING) @Column(name="detected_runtime",length=32) private ProjectRuntime detectedRuntime;
 private Integer applicationPort; @Column(length=1000) private String summary; @Column(columnDefinition="TEXT") private String evidence; @Column(length=1000) private String errorMessage;
 @Column(nullable=false) private Instant createdAt=Instant.now(); @Column(nullable=false) private Instant updatedAt=Instant.now();
 protected AnalyzerResult(){} public AnalyzerResult(Project project){this.project=project;}
 public Long getId(){return id;} public Project getProject(){return project;} public JobStatus getStatus(){return status;} public ProjectRuntime getDetectedRuntime(){return detectedRuntime;} public Integer getApplicationPort(){return applicationPort;} public String getSummary(){return summary;} public String getEvidence(){return evidence;} public String getErrorMessage(){return errorMessage;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public void complete(ProjectRuntime runtime,Integer port,String summary,String evidence){status=JobStatus.SUCCESS;detectedRuntime=runtime;applicationPort=port;this.summary=summary;this.evidence=evidence;updatedAt=Instant.now();} public void fail(String reason){status=JobStatus.FAILED;errorMessage=reason;updatedAt=Instant.now();}
}
