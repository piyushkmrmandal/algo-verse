# ─────────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first for layer caching
COPY pom.xml ./
COPY src ./src

# Build (skip tests in Docker build — tests run in CI)
RUN apk add --no-cache maven && \
    mvn package -DskipTests --no-transfer-progress

# ─────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user
RUN addgroup -S sysdesign && adduser -S sysdesign -G sysdesign

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown sysdesign:sysdesign app.jar

USER sysdesign

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
