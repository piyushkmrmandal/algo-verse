# =============================================================================
# Stage 1 — Build
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY pom.xml .

RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q || true

COPY src/ src/
RUN mvn package -DskipTests -q

# =============================================================================
# Stage 2 — Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="AlgoVerse <team@algoverse.io>"
LABEL service="collaboration-service"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/target/collaboration-service-*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8088/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
