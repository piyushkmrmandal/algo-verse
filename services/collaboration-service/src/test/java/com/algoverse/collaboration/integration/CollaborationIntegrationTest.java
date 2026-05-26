package com.algoverse.collaboration.integration;

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
 * Full-slice integration tests for collaboration-service.
 * Spins up PostgreSQL 16 and Redis 7 via Testcontainers.
 * JWT validation is disabled when jwt.public-key is blank (dev/test mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Collaboration Service Integration Tests")
class CollaborationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("collaboration_test")
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

        // Empty key → JwtService warns and skips RSA verification (dev/test mode)
        registry.add("jwt.public-key", () -> "");
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

    // ------------------------------------------------------------------
    // 3. Security — all room endpoints require authentication
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3. POST /api/v1/collab/rooms without auth → 401")
    void createRoom_unauthenticated_returns401() {
        Map<String, String> req = Map.of("language", "JAVA", "problemSlug", "two-sum");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/collab/rooms", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("4. GET /api/v1/collab/rooms/me without auth → 401")
    void getMyRooms_unauthenticated_returns401() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/collab/rooms/me", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("5. POST /api/v1/collab/rooms/join/{code} without auth → 401")
    void joinRoom_unauthenticated_returns401() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/v1/collab/rooms/join/ABC123", null, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("6. GET /api/v1/collab/rooms/{roomId} without auth → 401")
    void getRoom_unauthenticated_returns401() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/collab/rooms/00000000-0000-0000-0000-000000000001", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
