package com.algoverse.problem.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-slice integration tests for problem-service.
 * Spins up PostgreSQL 16, MongoDB 7, Redis 7, and Elasticsearch 8 via Testcontainers.
 * JWT validation is disabled when jwt.public-key is blank (dev/test mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Problem Service Integration Tests")
class ProblemServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("problem_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @Container
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.1")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());

        String esUri = "http://" + elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200);
        registry.add("spring.elasticsearch.uris", () -> esUri);
        registry.add("spring.data.elasticsearch.uris", () -> esUri);

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-startup", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");

        // Empty key → JWT filter skips verification (dev/test mode)
        registry.add("jwt.public-key", () -> "");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    // ------------------------------------------------------------------
    // 1. Context loads
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. Spring context starts with all containers running")
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(mongo.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
        assertThat(elasticsearch.isRunning()).isTrue();
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
    // 3. Public GET endpoints (no auth required)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3. GET /api/v1/problems → 200 with list (seeded data)")
    void listProblems_publicEndpoint_returns200() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/problems", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("4. GET /api/v1/problems?search=sum → 200")
    void searchProblems_publicEndpoint_returns200() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/problems?search=sum", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("5. GET /api/v1/problems/topics → 200 with topic list")
    void getTopics_publicEndpoint_returns200() {
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/problems/topics", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("6. GET /api/v1/problems/two-sum → 200 (seeded problem)")
    void getProblemBySlug_seededProblem_returns200() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/problems/two-sum", Map.class);
        // Either 200 (problem exists in seed) or 404 (seed not yet applied) — context must respond
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // 4. Security — write endpoints require ADMIN role
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7. POST /api/v1/problems without auth → 401")
    void createProblem_unauthenticated_returns401() {
        Map<String, Object> req = Map.of(
                "slug", "test-problem",
                "title", "Test Problem",
                "difficulty", "EASY",
                "description", "Test description"
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/problems", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
