package com.algoverse.analytics.service;

import com.algoverse.analytics.domain.RawEvent;
import com.algoverse.analytics.dto.UserAnalytics;
import com.algoverse.analytics.repository.RawEventRepository;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAnalyticsService unit tests")
class UserAnalyticsServiceTest {

    @Mock
    private RawEventRepository rawEventRepository;

    @InjectMocks
    private UserAnalyticsService userAnalyticsService;

    private static final String USER_ID = "user-abc-123";

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RawEvent acceptedSubmission(String difficulty, Instant occurredAt) {
        return RawEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("SUBMISSION_GRADED")
                .userId(USER_ID)
                .payload(Map.of(
                        "difficulty", difficulty,
                        "status", "ACCEPTED"
                ))
                .occurredAt(occurredAt)
                .build();
    }

    private RawEvent xpEvent(int xpAmount, String reason, Instant occurredAt) {
        return RawEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("XP_AWARDED")
                .userId(USER_ID)
                .payload(Map.of("xpAmount", xpAmount, "reason", reason))
                .occurredAt(occurredAt)
                .build();
    }

    private Instant daysAgo(int days) {
        return LocalDate.now().minusDays(days)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // ── Empty user ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("empty user")
    class EmptyUser {

        @BeforeEach
        void setUp() {
            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    eq(USER_ID), eq("XP_AWARDED"), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("returns zero totalSolves for user with no submissions")
        void getAnalytics_noSubmissions_zeroSolves() {
            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.totalSolves()).isZero();
            assertThat(result.currentStreak()).isZero();
            assertThat(result.solvesByDifficulty()).isEmpty();
            assertThat(result.xpHistory()).isEmpty();
            assertThat(result.solveHeatmap()).isEmpty();
        }

        @Test
        @DisplayName("userId is preserved in response")
        void getAnalytics_preservesUserId() {
            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);
            assertThat(result.userId()).isEqualTo(USER_ID);
        }
    }

    // ── Solve count and difficulty breakdown ──────────────────────────────────

    @Nested
    @DisplayName("solve count and difficulty")
    class SolveCountAndDifficulty {

        @Test
        @DisplayName("counts total solves correctly")
        void getAnalytics_countsTotalSolves() {
            List<RawEvent> solves = List.of(
                    acceptedSubmission("EASY", daysAgo(1)),
                    acceptedSubmission("MEDIUM", daysAgo(2)),
                    acceptedSubmission("HARD", daysAgo(3))
            );

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.totalSolves()).isEqualTo(3L);
        }

        @Test
        @DisplayName("groups solves by difficulty correctly")
        void getAnalytics_groupsSolvesByDifficulty() {
            List<RawEvent> solves = List.of(
                    acceptedSubmission("EASY", daysAgo(1)),
                    acceptedSubmission("EASY", daysAgo(2)),
                    acceptedSubmission("MEDIUM", daysAgo(3)),
                    acceptedSubmission("HARD", daysAgo(4))
            );

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.solvesByDifficulty())
                    .containsEntry("EASY", 2L)
                    .containsEntry("MEDIUM", 1L)
                    .containsEntry("HARD", 1L);
        }

        @Test
        @DisplayName("difficulty key is uppercased regardless of payload case")
        void getAnalytics_difficultyCaseInsensitive() {
            List<RawEvent> solves = List.of(acceptedSubmission("easy", daysAgo(0)));

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.solvesByDifficulty()).containsKey("EASY");
            assertThat(result.solvesByDifficulty()).doesNotContainKey("easy");
        }
    }

    // ── Streak calculation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("streak calculation")
    class StreakCalculation {

        @Test
        @DisplayName("streak is 1 when only today has a solve")
        void computeStreak_onlyToday_isOne() {
            List<RawEvent> solves = List.of(acceptedSubmission("EASY", daysAgo(0)));

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.currentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("streak counts consecutive days ending today")
        void computeStreak_consecutiveDays_countedCorrectly() {
            List<RawEvent> solves = List.of(
                    acceptedSubmission("EASY", daysAgo(0)),
                    acceptedSubmission("EASY", daysAgo(1)),
                    acceptedSubmission("EASY", daysAgo(2))
            );

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.currentStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("streak resets to zero when last solve was 2+ days ago")
        void computeStreak_gapInDays_streakIsZero() {
            // Last solve was 3 days ago — no streak today or yesterday
            List<RawEvent> solves = List.of(acceptedSubmission("MEDIUM", daysAgo(3)));

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.currentStreak()).isZero();
        }

        @Test
        @DisplayName("streak continues from yesterday (yesterday counts as active)")
        void computeStreak_lastSolveYesterday_streakIsOne() {
            List<RawEvent> solves = List.of(acceptedSubmission("HARD", daysAgo(1)));

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.currentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple solves on same day count as single streak day")
        void computeStreak_multipleSolvesSameDay_countedOnce() {
            List<RawEvent> solves = List.of(
                    acceptedSubmission("EASY", daysAgo(0)),
                    acceptedSubmission("MEDIUM", daysAgo(0)),
                    acceptedSubmission("HARD", daysAgo(0))
            );

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID)).thenReturn(solves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(solves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            // 3 solves on same day → only 1 unique day → streak = 1
            assertThat(result.currentStreak()).isEqualTo(1);
        }
    }

    // ── XP history ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("XP history")
    class XpHistory {

        @Test
        @DisplayName("XP events are sorted ascending by date")
        void getAnalytics_xpEventsSortedByDate() {
            List<RawEvent> xpEvents = List.of(
                    xpEvent(100, "solve", daysAgo(5)),
                    xpEvent(50, "badge", daysAgo(1)),
                    xpEvent(200, "streak", daysAgo(10))
            );

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    eq(USER_ID), eq("XP_AWARDED"), any(), any())).thenReturn(xpEvents);
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(Collections.emptyList());

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.xpHistory()).hasSize(3);
            // Must be sorted ascending: 10 days ago < 5 days ago < 1 day ago
            assertThat(result.xpHistory().get(0).xpAmount()).isEqualTo(200);
            assertThat(result.xpHistory().get(1).xpAmount()).isEqualTo(100);
            assertThat(result.xpHistory().get(2).xpAmount()).isEqualTo(50);
        }

        @Test
        @DisplayName("XP event reason and amount are mapped correctly")
        void getAnalytics_xpEventFieldsMappedCorrectly() {
            List<RawEvent> xpEvents = List.of(xpEvent(75, "problem-solve", daysAgo(0)));

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(xpEvents);
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(Collections.emptyList());

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            assertThat(result.xpHistory()).hasSize(1);
            UserAnalytics.XpEvent event = result.xpHistory().get(0);
            assertThat(event.xpAmount()).isEqualTo(75);
            assertThat(event.reason()).isEqualTo("problem-solve");
        }
    }

    // ── Solve heatmap ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("solve heatmap")
    class SolveHeatmap {

        @Test
        @DisplayName("heatmap groups solves by date key (yyyy-MM-dd)")
        void getAnalytics_heatmapGroupedByDate() {
            List<RawEvent> recentSolves = List.of(
                    acceptedSubmission("EASY", daysAgo(0)),
                    acceptedSubmission("EASY", daysAgo(0)),
                    acceptedSubmission("MEDIUM", daysAgo(1))
            );

            when(rawEventRepository.findAcceptedSubmissionsByUserId(USER_ID))
                    .thenReturn(recentSolves);
            when(rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                    any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(rawEventRepository.findAcceptedSubmissionsByUserIdAndDateRange(
                    any(), any(), any())).thenReturn(recentSolves);

            UserAnalytics result = userAnalyticsService.getUserAnalytics(USER_ID);

            String todayKey = LocalDate.now().toString();
            String yesterdayKey = LocalDate.now().minusDays(1).toString();

            assertThat(result.solveHeatmap()).containsEntry(todayKey, 2L);
            assertThat(result.solveHeatmap()).containsEntry(yesterdayKey, 1L);
        }
    }
}
