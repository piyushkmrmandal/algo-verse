package com.algoverse.execution.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-slice integration tests for execution-service.
 * Spins up PostgreSQL 16 and Redis 7 via Testcontainers.
 * JWT validation is disabled when jwt.public-key is blank (dev/test mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Execution Service Integration Tests")
class ExecutionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("execution_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-startup", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");

        // Empty key → JWT filter warns and passes through without validation (dev/test mode)
        registry.add("jwt.public-key", () -> "");

        // Stub out sandbox and problem-service — not exercised in integration tests
        registry.add("sandbox.docker-image", () -> "algoverse/sandbox:latest");
        registry.add("sandbox.runtime", () -> "runc");
        registry.add("problem-service.base-url", () -> "http://localhost:9999");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    // ------------------------------------------------------------------
    // 1. Context loads
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. Spring context starts with PostgreSQL and Redis running")
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    // ------------------------------------------------------------------
    // 2. Health
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2. GET /actuator/health → UP")
    void actuatorHealth_isUp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("3. GET /actuator/info → 200")
    void actuatorInfo_returns200() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/actuator/info", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // 3. Security — submission endpoints require authentication
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4. POST /api/v1/submissions without auth → 401")
    void submitCode_unauthenticated_returns401() {
        Map<String, String> req = Map.of(
                "problemSlug", "two-sum",
                "language", "JAVA",
                "sourceCode", "class Solution { public int[] twoSum(int[] n, int t) { return null; } }"
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/submissions", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("5. POST /api/v1/submissions/run without auth → 401")
    void runCode_unauthenticated_returns401() {
        Map<String, String> req = Map.of(
                "language", "PYTHON",
                "sourceCode", "print('hello')",
                "stdin", ""
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/submissions/run", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("6. GET /api/v1/submissions without auth → 401")
    void listSubmissions_unauthenticated_returns401() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/submissions", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
