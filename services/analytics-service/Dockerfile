# ============================================================
# AlgoVerse Analytics Service — Multi-stage Docker build
# ============================================================

# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom first (layer caching)
COPY pom.xml .
COPY .mvn/ .mvn/ 2>/dev/null || true

# Download dependencies separately for better layer caching
RUN --mount=type=cache,target=/root/.m2 \
    if [ -f mvnw ]; then \
        chmod +x mvnw && ./mvnw dependency:go-offline -B -q; \
    else \
        apk add --no-cache maven && mvn dependency:go-offline -B -q; \
    fi

# Copy source and build
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    if [ -f mvnw ]; then \
        ./mvnw package -DskipTests -B -q; \
    else \
        mvn package -DskipTests -B -q; \
    fi

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the built jar from builder
COPY --from=builder /build/target/analytics-service-*.jar app.jar

# Ensure the jar is owned by appuser
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8085

# Health check against the actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
