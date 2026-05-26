# =============================================================================
# Stage 1 — Build
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY pom.xml .

RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q || true

COPY src/ src/
RUN mvn package -Dmaven.test.skip=true -q

# =============================================================================
# Stage 2 — Runtime
# Note: execution-service spawns Docker containers for sandboxed code execution.
#       In production, mount the Docker socket or use a sidecar DinD container.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="AlgoVerse <team@algoverse.io>"
LABEL service="execution-service"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Docker CLI — needed to spawn sandbox containers
RUN apk add --no-cache docker-cli

WORKDIR /app

COPY --from=builder /workspace/target/execution-service-*.jar app.jar

RUN chown -R appuser:appgroup /app

# execution-service needs the docker socket; run as root or add to docker group
# In compose the socket is mounted as :ro for the daemon call only
USER appuser

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8087/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
