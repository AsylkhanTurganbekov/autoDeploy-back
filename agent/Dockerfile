FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
ARG DOCKER_GID=988
RUN apk add --no-cache docker-cli git openssh-client \
    && addgroup -S agent && adduser -S agent -G agent \
    && addgroup -S -g "${DOCKER_GID}" docker-socket && addgroup agent docker-socket \
    && apk add --no-cache su-exec
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
