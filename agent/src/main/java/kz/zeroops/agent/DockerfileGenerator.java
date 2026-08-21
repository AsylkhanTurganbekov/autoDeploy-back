package kz.zeroops.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Only fixed, reviewed templates are written; LLM text is never used as a Dockerfile. */
@Component
class DockerfileGenerator {
  private static final Map<String, String> TEMPLATES = Map.of(
      "NODE", "FROM node:22-alpine AS build\nWORKDIR /app\nCOPY package*.json ./\nRUN npm ci\nCOPY . .\nRUN npm run build\nFROM node:22-alpine\nWORKDIR /app\nENV NODE_ENV=production\nCOPY package*.json ./\nRUN npm ci --omit=dev\nCOPY --from=build /app/.next ./.next\nCOPY --from=build /app/public ./public\nCOPY --from=build /app/next.config.* ./\nUSER node\nEXPOSE 3000\nCMD [\"npm\", \"start\"]\n",
      "SPRING_BOOT", "FROM maven:3.9-eclipse-temurin-21 AS build\nWORKDIR /app\nCOPY pom.xml .\nRUN mvn -q -DskipTests dependency:go-offline\nCOPY src ./src\nRUN mvn -q -DskipTests package\nFROM eclipse-temurin:21-jre-alpine\nWORKDIR /app\nCOPY --from=build /app/target/*.jar app.jar\nUSER 10001\nEXPOSE 8080\nENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]\n",
      "PYTHON", "FROM python:3.12-alpine\nWORKDIR /app\nCOPY requirements.txt .\nRUN pip install --no-cache-dir -r requirements.txt\nCOPY . .\nUSER 10001\nEXPOSE 8000\nCMD [\"python\", \"-m\", \"gunicorn\", \"app:app\", \"--bind\", \"0.0.0.0:8000\"]\n",
      "GO", "FROM golang:1.23-alpine AS build\nWORKDIR /app\nCOPY go.mod go.sum ./\nRUN go mod download\nCOPY . .\nRUN CGO_ENABLED=0 go build -o /out/app .\nFROM gcr.io/distroless/static-debian12\nCOPY --from=build /out/app /app\nUSER nonroot:nonroot\nEXPOSE 8080\nENTRYPOINT [\"/app\"]\n"
  );

  Path ensure(Path servicePath, String runtime) throws IOException {
    Path dockerfile = servicePath.resolve("Dockerfile");
    if (Files.isRegularFile(dockerfile)) return dockerfile;
    String template = TEMPLATES.get(runtime);
    if (template == null) throw new IOException("No reviewed Dockerfile template exists for " + runtime);
    Files.writeString(dockerfile, template, StandardOpenOption.CREATE_NEW);
    return dockerfile;
  }
}
