package com.algoverse.sysdesign.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-slice integration tests for sysdesign-service.
 * Spins up PostgreSQL 16, MongoDB 7, Redis 7 via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Sysdesign Integration Tests")
class SysdesignIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("sysdesign_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

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

        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("1. Spring context loads with all containers running")
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(mongo.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    @Test
    @DisplayName("2. GET /api/v1/sysdesign/problems returns HTTP 200 with list")
    void getProblems_returnsOkWithList() {
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/sysdesign/problems",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("3. GET /api/v1/sysdesign/problems/{slug} for unknown slug returns 404")
    void getProblem_unknownSlug_returns404() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/v1/sysdesign/problems/no-such-problem", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("4. GET /api/v1/sysdesign/diagrams/me without auth returns 401")
    void getMyDiagrams_noAuth_returns401() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/v1/sysdesign/diagrams/me", String.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("5. POST /api/v1/sysdesign/diagrams without auth returns 401")
    void saveDiagram_noAuth_returns401() {
        Map<String, Object> body = Map.of(
                "problemId", "00000000-0000-0000-0000-000000000001",
                "title", "My Design",
                "nodes", List.of(),
                "edges", List.of(),
                "metadata", Map.of()
        );

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/sysdesign/diagrams", body, String.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("6. Actuator health returns UP")
    void actuatorHealth_isUp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/actuator/health", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }
}
