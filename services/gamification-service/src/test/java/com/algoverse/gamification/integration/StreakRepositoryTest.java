package com.algoverse.gamification.integration;

import com.algoverse.gamification.domain.UserStreak;
import com.algoverse.gamification.repository.UserStreakRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} slice for {@link UserStreakRepository}.
 *
 * <p>Runs against a real PostgreSQL container managed by Testcontainers.
 * Each test is wrapped in a transaction and rolled back automatically.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserStreakRepository Integration Tests")
class StreakRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gamification_streak_test")
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
    private UserStreakRepository userStreakRepository;

    // ------------------------------------------------------------------
    // 1. New streak defaults to zero
    // ------------------------------------------------------------------

    @Test
    @Rollback
    @DisplayName("1. newStreak — defaults currentStreak and longestStreak to 0")
    void newStreak_defaultsToZero() {
        UUID userId = UUID.randomUUID();
        UserStreak streak = new UserStreak(userId);

        userStreakRepository.save(streak);
        userStreakRepository.flush();

        Optional<UserStreak> found = userStreakRepository.findById(userId);

        assertThat(found).isPresent();
        assertThat(found.get().getCurrentStreak()).isZero();
        assertThat(found.get().getLongestStreak()).isZero();
        assertThat(found.get().getLastActivityDate()).isNull();
    }

    // ------------------------------------------------------------------
    // 2. Update streak persists lastActivityDate
    // ------------------------------------------------------------------

    @Test
    @Rollback
    @DisplayName("2. updateStreak — persists lastActivityDate correctly")
    void updateStreak_persistsLastActivityDate() {
        UUID userId = UUID.randomUUID();
        UserStreak streak = new UserStreak(userId);
        streak.setCurrentStreak(1);
        streak.setLongestStreak(1);

        LocalDate today = LocalDate.now();
        streak.setLastActivityDate(today);

        userStreakRepository.save(streak);
        userStreakRepository.flush();

        UserStreak saved = userStreakRepository.findById(userId).orElseThrow();
        assertThat(saved.getLastActivityDate()).isEqualTo(today);
        assertThat(saved.getCurrentStreak()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 3. Longest streak never decreases on update
    // ------------------------------------------------------------------

    @Test
    @Rollback
    @DisplayName("3. longestStreak — never decreases when currentStreak resets")
    void longestStreak_neverDecreasesOnUpdate() {
        UUID userId = UUID.randomUUID();
        UserStreak streak = new UserStreak(userId);
        streak.setCurrentStreak(10);
        streak.setLongestStreak(10);
        streak.setLastActivityDate(LocalDate.now().minusDays(5));

        userStreakRepository.save(streak);
        userStreakRepository.flush();

        // Simulate a streak reset (user missed days)
        UserStreak loaded = userStreakRepository.findById(userId).orElseThrow();
        int previousLongest = loaded.getLongestStreak();

        loaded.setCurrentStreak(1); // reset
        loaded.setLastActivityDate(LocalDate.now());
        // longestStreak must NOT be updated since currentStreak < longestStreak
        if (loaded.getCurrentStreak() > loaded.getLongestStreak()) {
            loaded.setLongestStreak(loaded.getCurrentStreak());
        }

        userStreakRepository.save(loaded);
        userStreakRepository.flush();

        UserStreak updated = userStreakRepository.findById(userId).orElseThrow();
        assertThat(updated.getCurrentStreak()).isEqualTo(1);
        assertThat(updated.getLongestStreak()).isEqualTo(previousLongest);
        assertThat(updated.getLongestStreak()).isGreaterThanOrEqualTo(updated.getCurrentStreak());
    }
}
