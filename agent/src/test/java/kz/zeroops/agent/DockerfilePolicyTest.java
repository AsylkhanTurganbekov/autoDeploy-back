package kz.zeroops.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerfilePolicyTest {
  @Test void acceptsAConventionalDockerfile() throws Exception {
    Path file=Files.createTempFile("Dockerfile", "");
    Files.writeString(file,"FROM eclipse-temurin:21-jre-alpine\nUSER 10001\nEXPOSE 8080\n");
    assertDoesNotThrow(() -> new DockerfilePolicy().verify(file));
  }
  @Test void rejectsHostEscapeMarkers() throws Exception {
    Path file=Files.createTempFile("Dockerfile", "");
    Files.writeString(file,"FROM alpine\nRUN echo /var/run/docker.sock\n");
    assertThrows(Exception.class,() -> new DockerfilePolicy().verify(file));
  }
}
