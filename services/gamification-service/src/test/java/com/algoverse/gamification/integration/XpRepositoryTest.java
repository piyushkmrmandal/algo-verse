package com.algoverse.gamification.integration;

import com.algoverse.gamification.domain.UserXp;
import com.algoverse.gamification.repository.UserXpRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} slice for {@link UserXpRepository}.
 *
 * <p>Runs against a real PostgreSQL container managed by Testcontainers.
 * Each test is wrapped in a transaction and rolled back automatically.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserXpRepository Integration Tests")
class XpRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gamification_xp_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private UserXpRepository userXpRepository;

    // ------------------------------------------------------------------
    // 1. Save and find
    // ------------------------------------------------------------------

    @Test
    @Rollback
    @DisplayName("1. saveAndFind — persists UserXp record correctly")
    void saveAndFind_userXp_persistsCorrectly() {
        UUID userId = UUID.randomUUID();
        UserXp userXp = new UserXp(userId);
        userXp.addXp(150);
        userXp.incrementSolves("EASY");
        userXp.incrementSolves("MEDIUM");

        userXpRepository.save(userXp);
        userXpRepository.flush();

        Optional<UserXp> found = userXpRepository.findById(userId);

        assertThat(found).isPresent();
        assertThat(found.get().getTotalXp()).isEqualTo(150);
        assertThat(found.get().getLevel()).isEqualTo(UserXp.computeLevel(150));
        assertThat(found.get().getEasySolves()).isEqualTo(1);
        assertThat(found.get().getMediumSolves()).isEqualTo(1);
        assertThat(found.get().getTotalSolves()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // 2. Update / increment
    // ------------------------------------------------------------------

    @Test
    @Rollback
    @DisplayName("2. updateXp — increments XP correctly for existing user")
    void updateXp_existingUser_incrementsCorrectly() {
        UUID userId = UUID.randomUUID();
        UserXp userXp = new UserXp(userId);
        userXp.addXp(100);
        userXpRepository.save(userXp);
        userXpRepository.flush();

        // Simulate a second award
        UserXp loaded = userXpRepository.findById(userId).orElseThrow();
        loaded.addXp(200);
        userXpRepository.save(loaded);
        userXpRepository.flush();

        UserXp updated = userXpRepository.findById(userId).orElseThrow();
        assertThat(updated.getTotalXp()).isEqualTo(300);
        assertThat(updated.getLevel()).isEqualTo(UserXp.computeLevel(300));
    }

    // ------------------------------------------------------------------
    // 3. Leaderboard ordering
    // ------------------------------------------------------------------

    @Test
    @Rollback
    @DisplayName("3. findAllByOrderByTotalXpDesc — returns users in descending XP order")
    void findTopByOrderByTotalXpDesc_returnsLeaderboardOrder() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();

        UserXp xp1 = new UserXp(user1);
        xp1.addXp(500);

        UserXp xp2 = new UserXp(user2);
        xp2.addXp(1000);

        UserXp xp3 = new UserXp(user3);
        xp3.addXp(250);

        userXpRepository.save(xp1);
        userXpRepository.save(xp2);
        userXpRepository.save(xp3);
        userXpRepository.flush();

        Page<UserXp> leaderboard = userXpRepository.findAllByOrderByTotalXpDesc(
                PageRequest.of(0, 10)
        );

        assertThat(leaderboard.getContent()).isNotEmpty();
        // The three users we saved should appear in descending order (1000, 500, 250)
        // Other users from prior tests may be present but sorted correctly too
        java.util.List<UserXp> content = leaderboard.getContent();
        for (int i = 0; i < content.size() - 1; i++) {
            assertThat(content.get(i).getTotalXp())
                    .isGreaterThanOrEqualTo(content.get(i + 1).getTotalXp());
        }

        // The top entry for our dataset should be user2 with 1000 XP
        assertThat(content.get(0).getTotalXp()).isGreaterThanOrEqualTo(1000);
    }
}
