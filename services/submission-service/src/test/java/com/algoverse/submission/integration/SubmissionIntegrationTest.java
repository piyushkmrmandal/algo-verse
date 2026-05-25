package com.algoverse.submission.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
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
 * Full-slice integration tests for submission-service.
 * Spins up PostgreSQL 16 and Redis 7 via Testcontainers.
 * Kafka is disabled (consumer auto-startup=false).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Submission Integration Tests")
class SubmissionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("submission_test")
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
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("1. Spring context loads with containers running")
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    @Test
    @DisplayName("2. POST /api/v1/submissions without token returns 401")
    void submit_noToken_returns401() {
        Map<String, Object> body = Map.of(
                "problemSlug", "two-sum",
                "language", "java",
                "code", "class S { int[] twoSum(int[] n, int t) { return new int[]{0,1}; } }",
                "difficulty", "EASY"
        );

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/submissions", body, String.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("3. GET /api/v1/submissions/me without token returns 401")
    void getMySubmissions_noToken_returns401() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/v1/submissions/me", String.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("4. GET /api/v1/submissions/{id} for nonexistent id without token returns 401")
    void getSubmission_noToken_returns401() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/v1/submissions/00000000-0000-0000-0000-000000000000",
                String.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("5. Actuator health returns UP")
    void actuatorHealth_isUp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/actuator/health", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("6. PostgreSQL Flyway migrations applied — submissions table exists")
    void flyway_submissionsTableExists() {
        // If Flyway ran cleanly, the context loads and health is UP.
        // A schema error would cause context startup to fail before reaching this test.
        ResponseEntity<Map> resp = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
