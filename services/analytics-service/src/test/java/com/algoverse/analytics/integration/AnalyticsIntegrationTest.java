package com.algoverse.analytics.integration;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-slice integration tests for analytics-service.
 * Spins up PostgreSQL 16, MongoDB 7, and a stub Redis via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Analytics Integration Tests")
class AnalyticsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("analytics_test")
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

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-startup", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
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
    @DisplayName("2. GET /api/v1/analytics/dashboard returns HTTP 200")
    void dashboard_returnsOk() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/analytics/dashboard", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("3. Dashboard totalUsers defaults to 0 on empty DB")
    void dashboard_emptyDb_totalUsersIsZero() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/analytics/dashboard", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number totalUsers = (Number) resp.getBody().get("totalUsers");
        assertThat(totalUsers).isNotNull();
        assertThat(totalUsers.longValue()).isZero();
    }

    @Test
    @DisplayName("4. GET /api/v1/analytics/platform/growth?period=7d returns 7 data points")
    void platformGrowth_7days_returns7DataPoints() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/analytics/platform/growth?period=7d", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("dataPoints");
        assertThat((java.util.List<?>) resp.getBody().get("dataPoints")).hasSize(7);
    }

    @Test
    @DisplayName("5. GET /api/v1/analytics/platform/growth?period=30d returns 30 data points")
    void platformGrowth_30days_returns30DataPoints() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/analytics/platform/growth?period=30d", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<?> points = (java.util.List<?>) resp.getBody().get("dataPoints");
        assertThat(points).hasSize(30);
    }

    @Test
    @DisplayName("6. GET /api/v1/analytics/problems/stats returns HTTP 200")
    void problemStats_returnsOk() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/analytics/problems/stats", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("7. Actuator health endpoint is UP")
    void actuatorHealth_isUp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/actuator/health", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }
}
