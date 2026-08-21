package kz.zeroops.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RepositoryScannerTest {
  @Test void findsMonorepoFrontendAndBackendWithoutExecutingAnything() throws Exception {
    Path root=Files.createTempDirectory("repo");
    Files.createDirectories(root.resolve("frontend"));
    Files.createDirectories(root.resolve("backend"));
    Files.writeString(root.resolve("frontend/package.json"),"{\"name\":\"web\"}");
    Files.writeString(root.resolve("backend/pom.xml"),"<project/>");
    var services=new RepositoryScanner().scan(root);
    assertEquals(2,services.size());
    assertTrue(services.stream().anyMatch(s -> s.path().equals("frontend") && s.runtime().equals("NODE")));
    assertTrue(services.stream().anyMatch(s -> s.path().equals("backend") && s.runtime().equals("SPRING_BOOT")));
  }
}
