# Build context: the repository root (see .dockerignore).
#   docker build -f deploy/Dockerfile -t multi-tour-commercial-reservation-api:dev .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
# Tests run in ci.yml (mvn verify); the image build only packages.
RUN mvn -q -B -pl commercial-reservation-app -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
# Numeric non-root user: the service never runs as root.
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app
COPY --from=build /src/commercial-reservation-app/target/commercial-reservation-app-*.jar /app/app.jar
USER 10001
EXPOSE 8080
# GET /health is public and answers {"status":"ok"} (HealthController); port from SERVER_PORT, default 8080.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO /dev/null "http://127.0.0.1:${SERVER_PORT:-8080}/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
