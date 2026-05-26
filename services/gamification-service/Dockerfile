# =============================================================================
# AlgoVerse :: Gamification Service — Multi-stage Docker build
# =============================================================================

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy POM first for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -q || true

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="AlgoVerse Platform Team"
LABEL service="gamification-service"
LABEL version="1.0.0"

# Create non-root user
RUN addgroup -S algoverse && adduser -S algoverse -G algoverse

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN chown algoverse:algoverse app.jar

USER algoverse

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
