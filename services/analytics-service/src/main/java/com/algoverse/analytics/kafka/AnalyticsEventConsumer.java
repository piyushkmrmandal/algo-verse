package com.algoverse.analytics.kafka;

import com.algoverse.analytics.domain.RawEvent;
import com.algoverse.analytics.repository.PlatformMetricRepository;
import com.algoverse.analytics.repository.ProblemStatRepository;
import com.algoverse.analytics.repository.RawEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka consumer for analytics event topics.
 * For each event:
 *  1. Persists a raw event document to MongoDB (async, fire-and-forget).
 *  2. Updates PostgreSQL aggregates synchronously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private static final String GROUP_ID = "analytics-service";

    private final RawEventRepository rawEventRepository;
    private final PlatformMetricRepository platformMetricRepository;
    private final ProblemStatRepository problemStatRepository;

    // -------------------------------------------------------------------------
    // Submission Graded
    // -------------------------------------------------------------------------

    @KafkaListener(
            topics = "algoverse.submission.graded",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubmissionGraded(@Payload Map<String, Object> event) {
        try {
            log.debug("Received submission.graded event: {}", event);

            String userId = extractString(event, "userId");
            String problemSlug = extractString(event, "problemSlug");
            String difficulty = Objects.toString(event.get("difficulty"), "MEDIUM").toUpperCase();
            String status = Objects.toString(event.get("status"), "").toUpperCase();
            boolean accepted = "ACCEPTED".equals(status);

            // 1. Save raw event async
            saveRawEventAsync("SUBMISSION_GRADED", userId, event);

            // 2. Upsert problem_stats
            if (problemSlug != null && !problemSlug.isBlank()) {
                problemStatRepository.upsertSubmission(problemSlug, difficulty, accepted ? 1 : 0);
            }

            // 3. Upsert daily platform metrics
            LocalDate today = LocalDate.now();
            platformMetricRepository.upsertIncrement(today, "daily_submissions", 1L);
            if (accepted) {
                platformMetricRepository.upsertIncrement(today, "daily_accepted", 1L);
            }

        } catch (Exception ex) {
            log.error("Error processing submission.graded event: {}", event, ex);
        }
    }

    // -------------------------------------------------------------------------
    // User Registered
    // -------------------------------------------------------------------------

    @KafkaListener(
            topics = "algoverse.user.registered",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(@Payload Map<String, Object> event) {
        try {
            log.debug("Received user.registered event: {}", event);

            String userId = extractString(event, "userId");

            saveRawEventAsync("USER_REGISTERED", userId, event);

            platformMetricRepository.upsertIncrement(LocalDate.now(), "daily_signups", 1L);

        } catch (Exception ex) {
            log.error("Error processing user.registered event: {}", event, ex);
        }
    }

    // -------------------------------------------------------------------------
    // XP Awarded
    // -------------------------------------------------------------------------

    @KafkaListener(
            topics = "algoverse.xp.awarded",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onXpAwarded(@Payload Map<String, Object> event) {
        try {
            log.debug("Received xp.awarded event: {}", event);

            String userId = extractString(event, "userId");
            long xpAmount = extractLong(event, "xpAmount", 0L);

            saveRawEventAsync("XP_AWARDED", userId, event);

            if (xpAmount > 0) {
                platformMetricRepository.upsertIncrement(LocalDate.now(), "daily_xp_awarded", xpAmount);
            }

        } catch (Exception ex) {
            log.error("Error processing xp.awarded event: {}", event, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Badge Earned
    // -------------------------------------------------------------------------

    @KafkaListener(
            topics = "algoverse.badge.earned",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBadgeEarned(@Payload Map<String, Object> event) {
        try {
            log.debug("Received badge.earned event: {}", event);

            String userId = extractString(event, "userId");

            saveRawEventAsync("BADGE_EARNED", userId, event);

            platformMetricRepository.upsertIncrement(LocalDate.now(), "daily_badges_earned", 1L);

        } catch (Exception ex) {
            log.error("Error processing badge.earned event: {}", event, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Async
    public void saveRawEventAsync(String eventType, String userId, Map<String, Object> payload) {
        try {
            RawEvent rawEvent = RawEvent.builder()
                    .eventType(eventType)
                    .userId(userId)
                    .payload(payload)
                    .occurredAt(Instant.now())
                    .build();
            rawEventRepository.save(rawEvent);
        } catch (Exception ex) {
            log.warn("Failed to persist raw event [type={}] to MongoDB: {}", eventType, ex.getMessage());
        }
    }

    private String extractString(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value != null ? value.toString() : null;
    }

    private long extractLong(Map<String, Object> event, String key, long defaultValue) {
        Object value = event.get(key);
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) return n.longValue();
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
