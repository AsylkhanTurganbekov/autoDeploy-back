package kz.zeroops.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Optional NITEC-compatible planner. It is advisory only: it sees static metadata,
 * cannot receive secrets/keys and its answer never becomes a shell or Docker command.
 */
@Service
public class DeploymentPlanner {
  private final String url, apiKey, model;
  public DeploymentPlanner(@Value("${AI_ADVISOR_URL:}") String url,
                           @Value("${AI_ADVISOR_API_KEY:}") String apiKey,
                           @Value("${AI_ADVISOR_MODEL:openai/gpt-oss-120b}") String model) {
    this.url=url; this.apiKey=apiKey; this.model=model;
  }
  public Plan plan(Project project, List<ProjectService> services) {
    List<Map<String,Object>> safeServices=services.stream().map(s -> Map.<String,Object>of(
        "key",s.getServiceKey(), "path",s.getRelativePath(), "runtime",s.getRuntime().name(),
        "internalPort",s.getInternalPort(), "visibility",s.getVisibility().name(),
        "dockerfileSource",s.getDockerfileSource().name())).toList();
    Map<String,Object> context=Map.of("repository",redactRepository(project.getRepositoryUrl()),"branch",project.getBranch(),"services",safeServices,"allowedPublicPortRange","18100-18999");
    if (url.isBlank() || apiKey.isBlank()) return new Plan("RULE_BASED", fallback(services), "LLM is not configured; the deterministic safe policy was used.", context);
    String prompt="Return JSON only: {\"selectedServiceKey\":string|null,\"summary\":string}. You are a deployment planner. Select at most one service whose visibility is PUBLIC. Never output commands, Dockerfile text, URLs with credentials, secrets, ports outside the supplied range, or a private service. Context: "+context;
    try {
      JsonNode response=RestClient.create().post().uri(url).header(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey).header(HttpHeaders.CONTENT_TYPE,"application/json")
          .body(Map.of("model",model,"messages",List.of(Map.of("role","user","content",prompt)),"temperature",0))
          .retrieve().body(JsonNode.class);
      String content=response==null?"":response.path("choices").path(0).path("message").path("content").asText("");
      JsonNode answer=new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
      String proposed=answer.path("selectedServiceKey").isTextual()?answer.path("selectedServiceKey").asText():null;
      boolean allowed=proposed!=null && services.stream().anyMatch(s -> s.getServiceKey().equals(proposed) && s.getVisibility()==ServiceVisibility.PUBLIC);
      String selected=allowed ? proposed : fallback(services);
      String summary=answer.path("summary").asText("Validated NITEC deployment recommendation.");
      return new Plan("NITEC",selected,summary.substring(0,Math.min(summary.length(),500)),context);
    } catch (Exception ignored) {
      return new Plan("RULE_BASED",fallback(services),"NITEC planner was unavailable; the deterministic safe policy was used.",context);
    }
  }
  private static String fallback(List<ProjectService> services) { return services.stream().filter(s -> s.getVisibility()==ServiceVisibility.PUBLIC).findFirst().map(ProjectService::getServiceKey).orElse(null); }
  private static String redactRepository(String value) { return value.replaceFirst("https://[^@/]+@", "https://"); }
  public record Plan(String source,String selectedServiceKey,String summary,Map<String,Object> sanitizedContext) { }
}
