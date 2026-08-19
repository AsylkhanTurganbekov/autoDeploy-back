FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S agent && adduser -S agent -G agent
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER agent
ENTRYPOINT ["java","-jar","/app/app.jar"]
