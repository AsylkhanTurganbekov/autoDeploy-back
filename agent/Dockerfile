FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache docker-cli git openssh-client \
    && addgroup -S agent && adduser -S agent -G agent \
    && addgroup -g 1992 deploykey && addgroup agent deploykey
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER agent
ENTRYPOINT ["java","-jar","/app/app.jar"]
