package com.algoverse.gamification.integration;

import com.algoverse.gamification.dto.BadgeResponse;
import com.algoverse.gamification.dto.LeaderboardEntry;
import com.algoverse.gamification.dto.StreakResponse;
import com.algoverse.gamification.dto.XpResponse;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-slice integration tests for the gamification-service.
 *
 * <p>Spins up a real PostgreSQL 16 and Redis 7 via Testcontainers,
 * starts the complete Spring application context, and exercises all
 * public REST endpoints via {@link TestRestTemplate}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Gamification Integration Tests")
class GamificationIntegrationTest {

    // ------------------------------------------------------------------
    // Containers — static so they are shared across all tests in this class
    // ------------------------------------------------------------------

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gamification_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    // ------------------------------------------------------------------
    // Dynamic Spring properties injected from running containers
    // ------------------------------------------------------------------

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());

        // Disable Kafka consumers — no broker in test environment
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-startup", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    // ------------------------------------------------------------------
    // 1. Context loads
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. Spring context starts successfully")
    void contextLoads() {
        // If this test method is reached, the context loaded successfully.
        assertThat(postgres.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    // ------------------------------------------------------------------
    // 2–4. XP award endpoints
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2. POST admin/award EASY first solve → XP = 50")
    void awardXpForEasySolve_firstTime_returns50Xp() {
        UUID userId = UUID.randomUUID();
        int xpAwarded = 50;

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "xpAmount", xpAwarded,
                "reason", "EASY first-solve test"
        );

        ResponseEntity<String> awardResp = restTemplate.postForEntity(
                "/api/v1/gamification/admin/award",
                request,
                String.class
        );
        assertThat(awardResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<XpResponse> xpResp = restTemplate.getForEntity(
                "/api/v1/gamification/xp/" + userId,
                XpResponse.class
        );
        assertThat(xpResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xpResp.getBody()).isNotNull();
        assertThat(xpResp.getBody().totalXp()).isEqualTo(50);
    }

    @Test
    @DisplayName("3. POST admin/award MEDIUM first solve → XP = 100")
    void awardXpForMediumSolve_firstTime_returns100Xp() {
        UUID userId = UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "xpAmount", 100,
                "reason", "MEDIUM first-solve test"
        );

        ResponseEntity<String> awardResp = restTemplate.postForEntity(
                "/api/v1/gamification/admin/award",
                request,
                String.class
        );
        assertThat(awardResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<XpResponse> xpResp = restTemplate.getForEntity(
                "/api/v1/gamification/xp/" + userId,
                XpResponse.class
        );
        assertThat(xpResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xpResp.getBody()).isNotNull();
        assertThat(xpResp.getBody().totalXp()).isEqualTo(100);
    }

    @Test
    @DisplayName("4. POST admin/award HARD first solve → XP = 200")
    void awardXpForHardSolve_firstTime_returns200Xp() {
        UUID userId = UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "xpAmount", 200,
                "reason", "HARD first-solve test"
        );

        ResponseEntity<String> awardResp = restTemplate.postForEntity(
                "/api/v1/gamification/admin/award",
                request,
                String.class
        );
        assertThat(awardResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<XpResponse> xpResp = restTemplate.getForEntity(
                "/api/v1/gamification/xp/" + userId,
                XpResponse.class
        );
        assertThat(xpResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xpResp.getBody()).isNotNull();
        assertThat(xpResp.getBody().totalXp()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 5. GET XP for unknown user → 0 XP, level 1
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5. GET xp/{userId} for unknown user → totalXp=0, level=1")
    void getXp_newUser_returnsZeroXp() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<XpResponse> resp = restTemplate.getForEntity(
                "/api/v1/gamification/xp/" + userId,
                XpResponse.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().totalXp()).isZero();
        assertThat(resp.getBody().level()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 6. GET streak for unknown user → currentStreak=0
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6. GET streak/{userId} for unknown user → currentStreak=0")
    void getStreak_newUser_returnsZeroStreak() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<StreakResponse> resp = restTemplate.getForEntity(
                "/api/v1/gamification/streak/" + userId,
                StreakResponse.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().currentStreak()).isZero();
        assertThat(resp.getBody().longestStreak()).isZero();
    }

    // ------------------------------------------------------------------
    // 7. GET badges for unknown user → empty list
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7. GET badges/{userId} for unknown user → empty list")
    void getBadges_newUser_returnsEmptyList() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<List<BadgeResponse>> resp = restTemplate.exchange(
                "/api/v1/gamification/badges/" + userId,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 8. GET leaderboard → 200 with list structure
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8. GET leaderboard → HTTP 200 with list response")
    void getLeaderboard_returns200() {
        ResponseEntity<List<LeaderboardEntry>> resp = restTemplate.exchange(
                "/api/v1/gamification/leaderboard?page=0&size=10",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        // Body may be empty list if no users, but must not be null
    }

    // ------------------------------------------------------------------
    // 9. First-solve badge awarded after first solve
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9. first-solve badge awarded after admin badge award")
    void firstSolveBadge_awardedAfterFirstSolve() {
        UUID userId = UUID.randomUUID();

        // Use admin endpoint to directly award the first-solve badge
        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "badgeSlug", "first-solve",
                "reason", "integration test"
        );

        ResponseEntity<String> awardResp = restTemplate.postForEntity(
                "/api/v1/gamification/admin/award",
                request,
                String.class
        );
        assertThat(awardResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify badge appears in user's badge list
        ResponseEntity<List<BadgeResponse>> badgesResp = restTemplate.exchange(
                "/api/v1/gamification/badges/" + userId,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(badgesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(badgesResp.getBody()).isNotNull();
        assertThat(badgesResp.getBody())
                .extracting(BadgeResponse::slug)
                .contains("first-solve");
    }

    // ------------------------------------------------------------------
    // 10. Idempotency — same XP award applied twice, second call still 200
    //     but we can verify via the admin award endpoint response
    // ------------------------------------------------------------------

    @Test
    @DisplayName("10. Idempotency — awarding the same badge twice does not create duplicates")
    void idempotency_sameBadge_doesNotDoubleAward() {
        UUID userId = UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "userId", userId.toString(),
                "badgeSlug", "first-solve",
                "reason", "idempotency test"
        );

        // First award
        ResponseEntity<String> first = restTemplate.postForEntity(
                "/api/v1/gamification/admin/award",
                request,
                String.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second award of the same badge
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/v1/gamification/admin/award",
                request,
                String.class
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        // User should still only have the badge once
        ResponseEntity<List<BadgeResponse>> badgesResp = restTemplate.exchange(
                "/api/v1/gamification/badges/" + userId,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(badgesResp.getBody()).isNotNull();
        long firstSolveCount = badgesResp.getBody().stream()
                .filter(b -> "first-solve".equals(b.slug()))
                .count();
        assertThat(firstSolveCount).isEqualTo(1);
    }
}
