# =============================================================================
# AlgoVerse — problem-service
# Multi-stage build: JDK builder → JRE runtime
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Install Maven and download dependencies (cached unless pom.xml changes)
RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -q || true

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="AlgoVerse <engineering@algoverse.io>"
LABEL org.opencontainers.image.title="problem-service"
LABEL org.opencontainers.image.description="AlgoVerse Problem Catalog Microservice"

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from builder
COPY --from=builder /workspace/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8082

# Health check against Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
