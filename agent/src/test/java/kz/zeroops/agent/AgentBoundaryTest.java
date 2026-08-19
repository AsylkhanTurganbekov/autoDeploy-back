package kz.zeroops.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentBoundaryTest {
  private final AgentBoundary boundary=new AgentBoundary();
  @Test void acceptsAllowlistedVerifiedManifest(){assertTrue(boundary.accepts(manifest("autodeploy/demo:abcdef1",Instant.now().plusSeconds(60))));}
  @Test void rejectsUnsafeInputs(){assertFalse(boundary.accepts(manifest("evil;command:abcdef1",Instant.now().plusSeconds(60))));assertFalse(boundary.accepts(manifest("autodeploy/demo:abcdef1",Instant.now().minusSeconds(1))));assertFalse(boundary.accepts(new AgentBoundary.Manifest("1","2","3","abcdef1","NODE",3000,"autodeploy/demo:abcdef1",Instant.now().plusSeconds(60),true)));}
  private AgentBoundary.Manifest manifest(String image,Instant expires){return new AgentBoundary.Manifest("1","2","3","abcdef1","NODE",3000,image,expires,false);}
}
