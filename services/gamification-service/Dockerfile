# =============================================================================
# AlgoVerse :: Gamification Service — Multi-stage Docker build
# =============================================================================

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom first (layer-cache friendly)
COPY pom.xml .
RUN test -f mvnw || (apk add --no-cache curl && \
    curl -sL https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz | tar xz -C /opt && \
    ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn)

COPY mvnw* ./
COPY .mvn .mvn

# Download dependencies
RUN if [ -f mvnw ]; then chmod +x mvnw && ./mvnw dependency:go-offline -q; \
    else mvn dependency:go-offline -q; fi

# Copy source and build
COPY src ./src
RUN if [ -f mvnw ]; then ./mvnw package -DskipTests -q; \
    else mvn package -DskipTests -q; fi

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
