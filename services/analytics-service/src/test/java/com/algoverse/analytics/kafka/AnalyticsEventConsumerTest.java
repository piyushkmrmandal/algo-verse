package com.algoverse.analytics.kafka;

import com.algoverse.analytics.repository.PlatformMetricRepository;
import com.algoverse.analytics.repository.ProblemStatRepository;
import com.algoverse.analytics.repository.RawEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsEventConsumer unit tests")
class AnalyticsEventConsumerTest {

    @Mock private RawEventRepository rawEventRepository;
    @Mock private PlatformMetricRepository platformMetricRepository;
    @Mock private ProblemStatRepository problemStatRepository;

    @InjectMocks private AnalyticsEventConsumer consumer;

    // ── submission.graded ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("onSubmissionGraded")
    class OnSubmissionGraded {

        @Test
        @DisplayName("ACCEPTED submission upserts problem stats and daily_submissions + daily_accepted")
        void onSubmissionGraded_accepted_updatesAllMetrics() {
            Map<String, Object> event = Map.of(
                    "userId", "user-1",
                    "problemSlug", "two-sum",
                    "difficulty", "EASY",
                    "status", "ACCEPTED"
            );

            consumer.onSubmissionGraded(event);

            // Problem stats upserted with accepted=1
            verify(problemStatRepository).upsertSubmission("two-sum", "EASY", 1);
            // Daily submission incremented
            verify(platformMetricRepository).upsertIncrement(any(LocalDate.class), eq("daily_submissions"), eq(1L));
            // Daily accepted incremented
            verify(platformMetricRepository).upsertIncrement(any(LocalDate.class), eq("daily_accepted"), eq(1L));
        }

        @Test
        @DisplayName("WRONG_ANSWER submission increments daily_submissions but NOT daily_accepted")
        void onSubmissionGraded_wrongAnswer_noAcceptedIncrement() {
            Map<String, Object> event = Map.of(
                    "userId", "user-2",
                    "problemSlug", "binary-search",
                    "difficulty", "MEDIUM",
                    "status", "WRONG_ANSWER"
            );

            consumer.onSubmissionGraded(event);

            verify(problemStatRepository).upsertSubmission("binary-search", "MEDIUM", 0);
            verify(platformMetricRepository).upsertIncrement(any(), eq("daily_submissions"), eq(1L));
            verify(platformMetricRepository, never()).upsertIncrement(any(), eq("daily_accepted"), anyLong());
        }

        @Test
        @DisplayName("missing problemSlug skips problem stats upsert")
        void onSubmissionGraded_missingSlug_skipsProblemStatUpsert() {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", "user-3");
            event.put("status", "ACCEPTED");
            // problemSlug intentionally absent

            consumer.onSubmissionGraded(event);

            verify(problemStatRepository, never()).upsertSubmission(any(), any(), anyInt());
            // But platform metrics still incremented
            verify(platformMetricRepository).upsertIncrement(any(), eq("daily_submissions"), eq(1L));
        }

        @Test
        @DisplayName("difficulty defaults to MEDIUM when missing from event")
        void onSubmissionGraded_missingDifficulty_defaultsMedium() {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", "user-4");
            event.put("problemSlug", "valid-parentheses");
            event.put("status", "ACCEPTED");
            // difficulty absent

            consumer.onSubmissionGraded(event);

            verify(problemStatRepository).upsertSubmission("valid-parentheses", "MEDIUM", 1);
        }
    }

    // ── user.registered ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("onUserRegistered")
    class OnUserRegistered {

        @Test
        @DisplayName("increments daily_signups metric")
        void onUserRegistered_incrementsSignups() {
            Map<String, Object> event = Map.of("userId", "new-user-99");

            consumer.onUserRegistered(event);

            verify(platformMetricRepository)
                    .upsertIncrement(any(LocalDate.class), eq("daily_signups"), eq(1L));
        }

        @Test
        @DisplayName("does not interact with problem stats on user registration")
        void onUserRegistered_noProblemStatInteraction() {
            consumer.onUserRegistered(Map.of("userId", "new-user-100"));

            verifyNoInteractions(problemStatRepository);
        }
    }

    // ── xp.awarded ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("onXpAwarded")
    class OnXpAwarded {

        @Test
        @DisplayName("positive xpAmount increments daily_xp_awarded by that amount")
        void onXpAwarded_positiveXp_incrementsByAmount() {
            Map<String, Object> event = Map.of("userId", "user-5", "xpAmount", 150);

            consumer.onXpAwarded(event);

            verify(platformMetricRepository)
                    .upsertIncrement(any(LocalDate.class), eq("daily_xp_awarded"), eq(150L));
        }

        @Test
        @DisplayName("zero xpAmount does NOT increment metric")
        void onXpAwarded_zeroXp_skipsMetricUpdate() {
            Map<String, Object> event = Map.of("userId", "user-6", "xpAmount", 0);

            consumer.onXpAwarded(event);

            verify(platformMetricRepository, never())
                    .upsertIncrement(any(), eq("daily_xp_awarded"), anyLong());
        }

        @Test
        @DisplayName("xpAmount as string is parsed correctly")
        void onXpAwarded_xpAmountAsString_parsedCorrectly() {
            Map<String, Object> event = Map.of("userId", "user-7", "xpAmount", "200");

            consumer.onXpAwarded(event);

            verify(platformMetricRepository)
                    .upsertIncrement(any(), eq("daily_xp_awarded"), eq(200L));
        }
    }

    // ── badge.earned ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("onBadgeEarned")
    class OnBadgeEarned {

        @Test
        @DisplayName("increments daily_badges_earned by 1")
        void onBadgeEarned_incrementsBadgesEarned() {
            Map<String, Object> event = Map.of(
                    "userId", "user-8",
                    "badgeSlug", "first-solve"
            );

            consumer.onBadgeEarned(event);

            verify(platformMetricRepository)
                    .upsertIncrement(any(LocalDate.class), eq("daily_badges_earned"), eq(1L));
        }

        @Test
        @DisplayName("does NOT interact with problem stats on badge event")
        void onBadgeEarned_noProblemStatInteraction() {
            consumer.onBadgeEarned(Map.of("userId", "user-9", "badgeSlug", "month-legend"));

            verifyNoInteractions(problemStatRepository);
        }
    }
}
