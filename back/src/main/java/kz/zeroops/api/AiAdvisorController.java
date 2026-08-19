package kz.zeroops.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Read-only advisor. It has no Docker, Agent or repository write capability. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/advisor")
public class AiAdvisorController {
  private final ProjectRepository projects; private final CurrentUser current; private final String url,key,model;
  public AiAdvisorController(ProjectRepository projects,CurrentUser current,@Value("${AI_ADVISOR_URL:}") String url,@Value("${AI_ADVISOR_API_KEY:}") String key,@Value("${AI_ADVISOR_MODEL:openai/gpt-oss-120b}") String model){this.projects=projects;this.current=current;this.url=url;this.key=key;this.model=model;}
  @PostMapping public Advice advise(@PathVariable Long projectId){Project p=projects.findById(projectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Project not found"));AppUser u=current.require();if(!current.isAdmin(u)&&!p.getOwner().getId().equals(u.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Project access denied");if(url.isBlank()||key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI advisor is not configured");Map<String,Object> context=Map.of("project",p.getName(),"runtime",p.getRuntime().name(),"port",p.getApplicationPort(),"domain",Optional.ofNullable(p.getDomain()).orElse("not configured"),"targetServer",p.getTargetServer()==null?"not assigned":p.getTargetServer().getName());String prompt="You are a read-only DevOps advisor. Return concise deployment risks and recommendations. Never request secrets or suggest executing commands. Context: "+context;try{JsonNode response=RestClient.create().post().uri(url).header(HttpHeaders.AUTHORIZATION,"Bearer "+key).header(HttpHeaders.CONTENT_TYPE,"application/json").body(Map.of("model",model,"messages",List.of(Map.of("role","user","content",prompt)),"temperature",0.2)).retrieve().body(JsonNode.class);String text=response==null?"No advisor response.":response.path("choices").path(0).path("message").path("content").asText("No advisor response.");return new Advice(text,context);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI advisor is unavailable");}}
  public record Advice(String recommendation,Map<String,Object> sanitizedContext){}
}
