package kz.zeroops.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reject obvious host-escape and unreviewed Dockerfile constructs before Docker sees them. */
@Component
class DockerfilePolicy {
  void verify(Path dockerfile) throws IOException {
    List<String> lines = Files.readAllLines(dockerfile);
    if (lines.size() > 400) throw new IOException("Dockerfile exceeds the reviewed policy size limit");
    for (String original : lines) {
      String line = original.trim().toLowerCase();
      if (line.startsWith("add ") || line.contains("/var/run/docker.sock") || line.contains("--privileged") || line.contains("--network=host") || line.contains("security-opt") || line.contains("cap-add")) {
        throw new IOException("Dockerfile violates the AutoDeploy build policy");
      }
    }
  }
}
