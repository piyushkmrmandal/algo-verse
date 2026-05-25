package com.algoverse.gamification.service;

import com.algoverse.gamification.domain.UserStreak;
import com.algoverse.gamification.dto.StreakResponse;
import com.algoverse.gamification.dto.XpResponse;
import com.algoverse.gamification.repository.UserStreakRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StreakService}.
 *
 * <p>Verifies streak increment on consecutive days, streak reset on missed days,
 * same-day idempotency, and milestone detection delegation to {@link XpService}.
 */
@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock
    private UserStreakRepository userStreakRepository;

    @Mock
    private XpService xpService;

    @InjectMocks
    private StreakService streakService;

    private static final UUID USER_ID = UUID.randomUUID();

    private XpResponse dummyXpResponse() {
        return new XpResponse(USER_ID, 25, 1, 75, 25, 25, 0, 0, 0, 0, Instant.now());
    }

    @BeforeEach
    void setUpXpServiceMock() {
        when(xpService.awardStreakBonus(any(UUID.class), anyInt()))
                .thenReturn(dummyXpResponse());
        when(xpService.getOrCreate(any(UUID.class)))
                .thenReturn(new com.algoverse.gamification.domain.UserXp(USER_ID));
    }

    // -----------------------------------------------------------------------
    // New user
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("New user (no prior streak record)")
    class NewUser {

        @BeforeEach
        void setUp() {
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("First solve starts streak at 1")
        void firstSolveStartsStreakAt1() {
            StreakResponse response = streakService.recordSolveForToday(USER_ID);
            assertThat(response.currentStreak()).isEqualTo(1);
            assertThat(response.longestStreak()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Consecutive days
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Consecutive day solve")
    class ConsecutiveDays {

        @Test
        @DisplayName("Solving on day N+1 increments streak")
        void consecutiveDayIncrementsStreak() {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            UserStreak streak = buildStreak(5, 5, yesterday);
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            StreakResponse response = streakService.recordSolveForToday(USER_ID);

            assertThat(response.currentStreak()).isEqualTo(6);
            assertThat(response.longestStreak()).isEqualTo(6);
        }

        @Test
        @DisplayName("Streak XP bonus is awarded on consecutive day")
        void streakBonusAwardedOnConsecutiveDay() {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            UserStreak streak = buildStreak(6, 6, yesterday);
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            streakService.recordSolveForToday(USER_ID);

            verify(xpService).awardStreakBonus(eq(USER_ID), eq(7));
        }

        @Test
        @DisplayName("Reaching 7-day streak triggers milestone bonus")
        void sevenDayStreakTriggersMilestone() {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            UserStreak streak = buildStreak(6, 6, yesterday);
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            streakService.recordSolveForToday(USER_ID);

            // XpService.awardStreakBonus(userId, 7) should be called — milestone logic lives there
            verify(xpService).awardStreakBonus(USER_ID, 7);
        }
    }

    // -----------------------------------------------------------------------
    // Missed day (streak reset)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Missed day — streak reset")
    class MissedDay {

        @Test
        @DisplayName("Missing one day resets streak to 1")
        void missingOneDayResetsStreakTo1() {
            LocalDate twoDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(2);
            UserStreak streak = buildStreak(10, 10, twoDaysAgo);
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            StreakResponse response = streakService.recordSolveForToday(USER_ID);

            assertThat(response.currentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("Longest streak is preserved after reset")
        void longestStreakPreservedAfterReset() {
            LocalDate twoDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(2);
            UserStreak streak = buildStreak(10, 15, twoDaysAgo); // longest was 15
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            StreakResponse response = streakService.recordSolveForToday(USER_ID);

            assertThat(response.currentStreak()).isEqualTo(1);
            assertThat(response.longestStreak()).isEqualTo(15);
        }

        @Test
        @DisplayName("Missing many days still resets streak to 1")
        void missingManyDaysResetsTo1() {
            LocalDate monthAgo = LocalDate.now(ZoneOffset.UTC).minusDays(30);
            UserStreak streak = buildStreak(30, 30, monthAgo);
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));
            when(userStreakRepository.save(any(UserStreak.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            StreakResponse response = streakService.recordSolveForToday(USER_ID);

            assertThat(response.currentStreak()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Same-day idempotency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Same-day idempotency")
    class SameDay {

        @Test
        @DisplayName("Solving twice on the same day does not increment streak")
        void solvingTwiceSameDayDoesNotIncrement() {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            UserStreak streak = buildStreak(5, 5, today);
            when(userStreakRepository.findById(USER_ID)).thenReturn(Optional.of(streak));

            StreakResponse response = streakService.recordSolveForToday(USER_ID);

            assertThat(response.currentStreak()).isEqualTo(5);
            // Should NOT save or award XP since already counted today
            verify(userStreakRepository, never()).save(any());
            verify(xpService, never()).awardStreakBonus(any(), anyInt());
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private UserStreak buildStreak(int current, int longest, LocalDate lastActivity) {
        UserStreak streak = new UserStreak(USER_ID);
        streak.setCurrentStreak(current);
        streak.setLongestStreak(longest);
        streak.setLastActivityDate(lastActivity);
        return streak;
    }
}
