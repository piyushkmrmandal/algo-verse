FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
RUN apk add --no-cache maven && mvn dependency:go-offline -q || true
COPY src/ src/
RUN mvn package -Dmaven.test.skip=true -q

FROM eclipse-temurin:21-jre-alpine AS runtime
LABEL service="visualization-service"

# Need Python3 and Node.js for the tracing subprocesses
RUN apk add --no-cache python3 nodejs

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /workspace/target/visualization-service-*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8092
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8092/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
