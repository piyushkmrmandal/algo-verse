package com.algoverse.auth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Full-slice integration tests for auth-service.
 * Spins up real PostgreSQL 16 and Redis 7 containers via Testcontainers,
 * exercises the register → login → /me flow end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Auth Service Integration Tests")
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("auth_test")
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

        // Kafka producer — keep a fake address; send failures are async and non-blocking
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");

        // OAuth2 providers — changeme defaults are fine; OAuth2 flows are not exercised here
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-secret");
        registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-id");
        registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-secret");
        registry.add("spring.security.oauth2.client.registration.linkedin.client-id", () -> "test-id");
        registry.add("spring.security.oauth2.client.registration.linkedin.client-secret", () -> "test-secret");
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
    // 2. Health check
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2. GET /actuator/health → UP")
    void actuatorHealth_isUp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }

    // ------------------------------------------------------------------
    // 3. Register
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3. POST /api/v1/auth/register with valid data → 201")
    void register_validData_returns201() {
        Map<String, String> req = Map.of(
                "email", "register_test@example.com",
                "password", "SecurePass1!",
                "displayName", "Integration Tester"
        );

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/register", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("accessToken");
        assertThat(resp.getBody()).containsKey("refreshToken");
    }

    @Test
    @DisplayName("4. POST /api/v1/auth/register with invalid email → 400")
    void register_invalidEmail_returns400() {
        Map<String, String> req = Map.of(
                "email", "not-an-email",
                "password", "SecurePass1!",
                "displayName", "Bad Email User"
        );

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/register", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("5. POST /api/v1/auth/register duplicate email → 409")
    void register_duplicateEmail_returns409() {
        Map<String, String> req = Map.of(
                "email", "duplicate@example.com",
                "password", "SecurePass1!",
                "displayName", "First User"
        );

        // First registration succeeds
        restTemplate.postForEntity("/api/v1/auth/register", req, Map.class);

        // Second registration with same email fails
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/register", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // 4. Login
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6. POST /api/v1/auth/login with valid credentials → 200 + tokens")
    void login_validCredentials_returns200WithTokens() {
        // Register first
        String email = "login_test@example.com";
        String password = "SecurePass1!";
        Map<String, String> registerReq = Map.of("email", email, "password", password, "displayName", "Login Test");
        restTemplate.postForEntity("/api/v1/auth/register", registerReq, Map.class);

        // Login
        Map<String, String> loginReq = Map.of("email", email, "password", password);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("accessToken");
        assertThat(resp.getBody()).containsKey("refreshToken");
        assertThat((String) resp.getBody().get("accessToken")).isNotBlank();
    }

    @Test
    @DisplayName("7. POST /api/v1/auth/login with wrong password → 401")
    void login_wrongPassword_returns401() {
        // Register first
        String email = "wrong_pass@example.com";
        Map<String, String> registerReq = Map.of("email", email, "password", "CorrectPass1!", "displayName", "Wrong Pass");
        restTemplate.postForEntity("/api/v1/auth/register", registerReq, Map.class);

        // Login with wrong password
        Map<String, String> loginReq = Map.of("email", email, "password", "WrongPassword!");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // 5. /me endpoint
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8. GET /api/v1/auth/me without token → 401")
    void me_withoutToken_returns401() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/auth/me", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("9. GET /api/v1/auth/me with valid token → 200 + user profile")
    void me_withValidToken_returns200() {
        // Register + login to get access token
        String email = "me_test@example.com";
        String password = "SecurePass1!";
        Map<String, String> registerReq = Map.of("email", email, "password", password, "displayName", "Me Test");
        restTemplate.postForEntity("/api/v1/auth/register", registerReq, Map.class);

        Map<String, String> loginReq = Map.of("email", email, "password", password);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        String accessToken = (String) loginResp.getBody().get("accessToken");

        // Call /me with Bearer token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> meResp = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResp.getBody()).containsKey("email");
        assertThat(meResp.getBody().get("email")).isEqualTo(email);
    }

    // ------------------------------------------------------------------
    // 6. Token refresh
    // ------------------------------------------------------------------

    @Test
    @DisplayName("10. POST /api/v1/auth/refresh with valid refresh token → 200 + new tokens")
    void refresh_validToken_returnsNewTokens() {
        String email = "refresh_test@example.com";
        String password = "SecurePass1!";
        Map<String, String> registerReq = Map.of("email", email, "password", password, "displayName", "Refresh Test");
        restTemplate.postForEntity("/api/v1/auth/register", registerReq, Map.class);

        Map<String, String> loginReq = Map.of("email", email, "password", password);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        String refreshToken = (String) loginResp.getBody().get("refreshToken");

        Map<String, String> refreshReq = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map> refreshResp = restTemplate.postForEntity("/api/v1/auth/refresh", refreshReq, Map.class);

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResp.getBody()).containsKey("accessToken");
    }
}
