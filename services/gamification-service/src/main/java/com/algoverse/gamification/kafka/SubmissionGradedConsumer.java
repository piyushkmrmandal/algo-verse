package com.algoverse.gamification.kafka;

import com.algoverse.gamification.service.BadgeService;
import com.algoverse.gamification.service.StreakService;
import com.algoverse.gamification.service.XpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Kafka consumer for the {@code algoverse.submission.graded} topic.
 *
 * <h3>Expected event shape</h3>
 * <pre>{@code
 * {
 *   "submissionId": "uuid",
 *   "userId": "uuid",
 *   "problemSlug": "two-sum",
 *   "difficulty": "EASY",
 *   "status": "ACCEPTED",
 *   "executionTimeMs": 42,
 *   "isFirstSolve": true,
 *   "gradedAt": "2026-05-25T10:00:00Z"
 * }
 * }</pre>
 *
 * <h3>Processing pipeline</h3>
 * <ol>
 *   <li>Idempotency check via Redis (TTL 24h)</li>
 *   <li>Filter: only process ACCEPTED submissions</li>
 *   <li>Award XP for first-solve based on difficulty</li>
 *   <li>Update streak and award streak bonus XP</li>
 *   <li>Evaluate and award badges</li>
 *   <li>Mark submission as processed in Redis</li>
 *   <li>Manual ack</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionGradedConsumer {

    private static final String TOPIC = "algoverse.submission.graded";
    private static final String GROUP_ID = "gamification-service";
    private static final String REDIS_IDEMPOTENCY_PREFIX = "gami:processed:graded:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final XpService xpService;
    private final StreakService streakService;
    private final BadgeService badgeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics           = TOPIC,
            groupId          = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubmissionGraded(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("kafkaTopic", record.topic());
        MDC.put("kafkaOffset", String.valueOf(record.offset()));

        try {
            JsonNode event = objectMapper.readTree(record.value());

            String submissionId   = event.path("submissionId").asText();
            String userId         = event.path("userId").asText();
            String status         = event.path("status").asText();
            String difficulty     = event.path("difficulty").asText("EASY");
            boolean isFirstSolve  = event.path("isFirstSolve").asBoolean(false);
            long executionTimeMs  = event.path("executionTimeMs").asLong(0);

            MDC.put("submissionId", submissionId);
            MDC.put("userId", userId);

            // ----------------------------------------------------------------
            // Idempotency guard
            // ----------------------------------------------------------------
            if (isAlreadyProcessed(submissionId)) {
                log.info("Duplicate submission event skipped: submissionId={}", submissionId);
                ack.acknowledge();
                return;
            }

            // ----------------------------------------------------------------
            // Only process ACCEPTED submissions
            // ----------------------------------------------------------------
            if (!"ACCEPTED".equals(status)) {
                log.debug("Submission not ACCEPTED (status={}), skipping: submissionId={}", status, submissionId);
                markAsProcessed(submissionId);
                ack.acknowledge();
                return;
            }

            UUID userUUID = UUID.fromString(userId);
            log.info("Processing gamification pipeline: submissionId={} userId={} difficulty={} isFirstSolve={}",
                    submissionId, userId, difficulty, isFirstSolve);

            // ----------------------------------------------------------------
            // Step 1: Award XP for solve
            // ----------------------------------------------------------------
            xpService.awardForSolve(userUUID, difficulty, isFirstSolve);

            // ----------------------------------------------------------------
            // Step 2: Update streak (also awards streak bonus XP internally)
            // ----------------------------------------------------------------
            var streakResponse = streakService.recordSolveForToday(userUUID);

            // ----------------------------------------------------------------
            // Step 3: Evaluate and award badges
            // ----------------------------------------------------------------
            var earnedBadges = badgeService.evaluateAndAward(
                    userUUID,
                    difficulty,
                    isFirstSolve,
                    streakResponse.currentStreak(),
                    executionTimeMs
            );

            markAsProcessed(submissionId);
            ack.acknowledge();

            log.info("Gamification pipeline complete: submissionId={} userId={} streak={} badgesEarned={}",
                    submissionId, userId, streakResponse.currentStreak(), earnedBadges.size());

        } catch (Exception e) {
            log.error("Gamification processing failed at offset={}: {}", record.offset(), e.getMessage(), e);
            // Do NOT ack — Spring Kafka DefaultErrorHandler will retry up to 3 times then DLQ.
            throw new RuntimeException("Gamification processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Idempotency (Redis)
    // -----------------------------------------------------------------------

    private boolean isAlreadyProcessed(String submissionId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(REDIS_IDEMPOTENCY_PREFIX + submissionId));
    }

    private void markAsProcessed(String submissionId) {
        stringRedisTemplate.opsForValue()
                .set(REDIS_IDEMPOTENCY_PREFIX + submissionId, "1",
                        IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
    }
}
