package kz.zeroops.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Static repository inspection.  It deliberately never executes package-manager,
 * build, or application commands: an untrusted repository is data at this stage.
 */
@Component
class RepositoryScanner {
  record Service(String key, String path, String runtime, int port, boolean publicCandidate,
                 boolean hasDockerfile, List<String> evidence) { }

  List<Service> scan(Path root) throws IOException {
    List<Service> found = new ArrayList<>();
    inspect(root, root, found);
    try (Stream<Path> paths = Files.walk(root, 3)) {
      paths.filter(Files::isDirectory)
          .filter(path -> !path.equals(root))
          .filter(path -> !path.toString().contains("/.git"))
          .forEach(path -> inspect(root, path, found));
    }
    return found.stream().distinct().toList();
  }

  private void inspect(Path root, Path dir, List<Service> found) {
    String relative = root.relativize(dir).toString().replace('\\', '/');
    if (relative.isBlank()) relative = ".";
    try {
      boolean dockerfile = Files.isRegularFile(dir.resolve("Dockerfile"));
      boolean node = Files.isRegularFile(dir.resolve("package.json"));
      boolean spring = Files.isRegularFile(dir.resolve("pom.xml")) || Files.isRegularFile(dir.resolve("build.gradle")) || Files.isRegularFile(dir.resolve("build.gradle.kts"));
      boolean python = Files.isRegularFile(dir.resolve("requirements.txt")) || Files.isRegularFile(dir.resolve("pyproject.toml"));
      boolean go = Files.isRegularFile(dir.resolve("go.mod"));
      boolean dotnet;
      try (Stream<Path> children = Files.list(dir)) {
        dotnet = children.anyMatch(p -> p.getFileName().toString().endsWith(".csproj"));
      }
      if (!(dockerfile || node || spring || python || go || dotnet)) return;
      String runtime = spring ? "SPRING_BOOT" : node ? "NODE" : python ? "PYTHON" : go ? "GO" : dotnet ? "DOTNET" : "DOCKERFILE";
      boolean publicCandidate = node && (relative.toLowerCase(Locale.ROOT).contains("front") || relative.equals("."));
      if (spring || python || go || dotnet) publicCandidate = !relative.toLowerCase(Locale.ROOT).contains("backend");
      int port = runtime.equals("SPRING_BOOT") ? 8080 : runtime.equals("NODE") ? 3000 : runtime.equals("PYTHON") ? 8000 : runtime.equals("GO") ? 8080 : 8080;
      List<String> evidence = new ArrayList<>();
      if (dockerfile) evidence.add("Dockerfile");
      if (node) evidence.add("package.json");
      if (spring) evidence.add("Spring build file");
      if (python) evidence.add("Python dependency file");
      if (go) evidence.add("go.mod");
      if (dotnet) evidence.add(".csproj");
      found.add(new Service(key(relative), relative, runtime, port, publicCandidate, dockerfile, List.copyOf(evidence)));
    } catch (IOException ignored) {
      // A malformed or unreadable directory is simply not a deployable service.
    }
  }

  private static String key(String path) {
    return path.equals(".") ? "root" : path.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT);
  }
}
