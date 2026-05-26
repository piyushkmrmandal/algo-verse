package com.algoverse.gamification.kafka;

import com.algoverse.gamification.dto.BadgeResponse;
import com.algoverse.gamification.dto.StreakResponse;
import com.algoverse.gamification.dto.XpResponse;
import com.algoverse.gamification.service.BadgeService;
import com.algoverse.gamification.service.StreakService;
import com.algoverse.gamification.service.XpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Kafka consumer that processes {@code algoverse.submissions.judged} events and
 * drives the gamification pipeline: XP calculation → streak update → badge check.
 *
 * <h3>Idempotency</h3>
 * <p>Uses Redis as a processed-event store keyed by {@code submissionId} with a
 * 24-hour TTL. Duplicate messages (caused by Kafka re-delivery on consumer
 * restart) are detected and silently skipped after ack to avoid double-awarding.
 *
 * <h3>Retry &amp; DLQ</h3>
 * <p>Spring Kafka {@code DefaultErrorHandler} is configured in
 * {@code KafkaConsumerConfig} with 3 retries and exponential backoff (1s, 2s, 4s).
 * After exhausting retries the message is forwarded to
 * {@code algoverse.submissions.judged.dlq} via {@code DeadLetterPublishingRecoverer}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionJudgedConsumer {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final String TOPIC_SUBMISSIONS_JUDGED  = "algoverse.submissions.judged";
    private static final String TOPIC_XP_UPDATED          = "algoverse.users.xp-updated";
    private static final String TOPIC_STREAKS_UPDATED     = "algoverse.streaks.updated";
    private static final String TOPIC_BADGES_EARNED       = "algoverse.badges.earned";
    private static final String TOPIC_PROBLEMS_SOLVED_1ST = "algoverse.problems.solved-first-time";

    private static final String GROUP_ID = "gamification-service";

    /** Redis key prefix for idempotency deduplication */
    private static final String REDIS_IDEMPOTENCY_PREFIX = "gami:processed:submission:";

    /** TTL for the Redis idempotency key — 24 hours */
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private static final int SCHEMA_VERSION = 1;

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final XpService xpService;
    private final StreakService streakService;
    private final BadgeService badgeService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:gamification-service}")
    private String serviceId;

    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;

    // -----------------------------------------------------------------------
    // Listener
    // -----------------------------------------------------------------------

    @KafkaListener(
            topics           = TOPIC_SUBMISSIONS_JUDGED,
            groupId          = GROUP_ID,
            concurrency      = "12",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubmissionJudged(ConsumerRecord<String, String> record, Acknowledgment ack) {

        String correlationId = extractHeader(record, "x-algoverse-correlation-id");
        MDC.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        MDC.put("kafkaTopic",     record.topic());
        MDC.put("kafkaPartition", String.valueOf(record.partition()));
        MDC.put("kafkaOffset",    String.valueOf(record.offset()));

        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload  = envelope.path("payload");

            String submissionId     = payload.path("submissionId").asText();
            String userId           = payload.path("userId").asText();
            String problemId        = payload.path("problemId").asText();
            String problemSlug      = payload.path("problemSlug").asText();
            String status           = payload.path("status").asText();
            String difficulty       = payload.path("difficulty").asText("EASY");
            boolean isFirstAccepted = payload.path("isFirstAccepted").asBoolean(false);
            long runtimeMs          = payload.path("runtimeMs").asLong(0);
            double memoryMb         = payload.path("memoryMb").asDouble(0);
            String language         = payload.path("language").asText();
            String causationId      = envelope.path("eventId").asText();

            MDC.put("submissionId", submissionId);
            MDC.put("userId",       userId);

            // ----------------------------------------------------------------
            // Idempotency check
            // ----------------------------------------------------------------
            if (isAlreadyProcessed(submissionId)) {
                log.info("[{}] Skipping duplicate submission event submissionId={} correlationId={}",
                        serviceId, submissionId, MDC.get("correlationId"));
                ack.acknowledge();
                return;
            }

            // Only award XP/streaks/badges on ACCEPTED submissions
            if (!"ACCEPTED".equals(status)) {
                log.debug("[{}] Submission not ACCEPTED (status={}), skipping gamification for submissionId={}",
                        serviceId, status, submissionId);
                markAsProcessed(submissionId);
                ack.acknowledge();
                return;
            }

            log.info("[{}] Processing gamification for submissionId={} userId={} difficulty={} isFirstAccepted={}",
                    serviceId, submissionId, userId, difficulty, isFirstAccepted);

            UUID userUUID = UUID.fromString(userId);

            // ----------------------------------------------------------------
            // Step 1: Award XP
            // ----------------------------------------------------------------
            XpResponse xpResponse = xpService.awardForSolve(userUUID, difficulty, isFirstAccepted);

            log.info("[{}] XP awarded userId={} totalXp={} level={}",
                    serviceId, userId, xpResponse.totalXp(), xpResponse.level());

            // ----------------------------------------------------------------
            // Step 2: Update streak
            // ----------------------------------------------------------------
            StreakResponse streakResponse = streakService.recordSolveForToday(userUUID);

            log.info("[{}] Streak updated userId={} currentStreak={} longestStreak={}",
                    serviceId, userId, streakResponse.currentStreak(), streakResponse.longestStreak());

            // ----------------------------------------------------------------
            // Step 3: Badge check
            // ----------------------------------------------------------------
            List<BadgeResponse> earnedBadges = badgeService.evaluateAndAward(
                    userUUID,
                    difficulty,
                    isFirstAccepted,
                    streakResponse.currentStreak(),
                    runtimeMs
            );

            // ----------------------------------------------------------------
            // Step 4: Publish downstream events
            // ----------------------------------------------------------------
            publishXpUpdatedEvent(userId, xpResponse, submissionId, causationId);
            publishStreakUpdatedEvent(userId, streakResponse, causationId);

            for (BadgeResponse badge : earnedBadges) {
                publishBadgeEarnedEvent(userId, badge, causationId);
                log.info("[{}] Badge earned userId={} badgeSlug={} rarity={}",
                        serviceId, userId, badge.slug(), badge.rarity());
            }

            if (isFirstAccepted) {
                publishProblemSolvedFirstTimeEvent(
                        userId, problemId, problemSlug, difficulty, language,
                        runtimeMs, memoryMb, submissionId,
                        xpResponse.totalSolves(), causationId
                );
            }

            // ----------------------------------------------------------------
            // Finalize
            // ----------------------------------------------------------------
            markAsProcessed(submissionId);
            ack.acknowledge();

            log.info("[{}] Gamification pipeline complete for submissionId={} userId={} "
                            + "totalXp={} streak={} badgesEarned={}",
                    serviceId, submissionId, userId, xpResponse.totalXp(),
                    streakResponse.currentStreak(), earnedBadges.size());

        } catch (Exception e) {
            MDC.put("errorClass", e.getClass().getSimpleName());
            log.error("[{}] Error processing submission.judged event correlationId={} topic={} "
                            + "partition={} offset={}: {}",
                    serviceId, MDC.get("correlationId"), record.topic(),
                    record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Gamification processing failed for record at offset "
                    + record.offset(), e);
        } finally {
            MDC.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Idempotency (Redis)
    // -----------------------------------------------------------------------

    private boolean isAlreadyProcessed(String submissionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_IDEMPOTENCY_PREFIX + submissionId));
    }

    private void markAsProcessed(String submissionId) {
        redisTemplate.opsForValue()
                .set(REDIS_IDEMPOTENCY_PREFIX + submissionId, "1", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
    }

    // -----------------------------------------------------------------------
    // Downstream event publishers
    // -----------------------------------------------------------------------

    private void publishXpUpdatedEvent(
            String userId,
            XpResponse xpResponse,
            String submissionId,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",      userId);
        payload.put("newTotalXp",  xpResponse.totalXp());
        payload.put("newLevel",    xpResponse.level());
        payload.put("referenceId", submissionId);

        publish(TOPIC_XP_UPDATED, userId,
                buildEnvelope(UUID.randomUUID().toString(), "user.xp-updated",
                        userId, "User", causationId, payload, userId));
    }

    private void publishStreakUpdatedEvent(
            String userId,
            StreakResponse streakResponse,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",        userId);
        payload.put("currentStreak", streakResponse.currentStreak());
        payload.put("longestStreak", streakResponse.longestStreak());

        publish(TOPIC_STREAKS_UPDATED, userId,
                buildEnvelope(UUID.randomUUID().toString(), "streak.updated",
                        userId, "User", causationId, payload, userId));
    }

    private void publishBadgeEarnedEvent(
            String userId,
            BadgeResponse badge,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",           userId);
        payload.put("badgeId",          badge.badgeId() != null ? badge.badgeId().toString() : null);
        payload.put("badgeSlug",        badge.slug());
        payload.put("badgeName",        badge.name());
        payload.put("badgeDescription", badge.description());
        payload.put("rarity",           badge.rarity());
        payload.put("xpReward",         badge.xpReward());

        publish(TOPIC_BADGES_EARNED, userId,
                buildEnvelope(UUID.randomUUID().toString(), "badge.earned",
                        badge.slug(), "Badge", causationId, payload, userId));
    }

    private void publishProblemSolvedFirstTimeEvent(
            String userId,
            String problemId,
            String problemSlug,
            String difficulty,
            String language,
            long runtimeMs,
            double memoryMb,
            String submissionId,
            int totalSolvedCount,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",           userId);
        payload.put("problemId",        problemId);
        payload.put("problemSlug",      problemSlug);
        payload.put("difficulty",       difficulty);
        payload.put("language",         language);
        payload.put("runtimeMs",        runtimeMs);
        payload.put("memoryMb",         memoryMb);
        payload.put("submissionId",     submissionId);
        payload.put("totalSolvedCount", totalSolvedCount);

        publish(TOPIC_PROBLEMS_SOLVED_1ST, userId,
                buildEnvelope(UUID.randomUUID().toString(), "problem.solved-first-time",
                        problemId, "Problem", causationId, payload, userId));
    }

    private void publish(String topic, String key, Map<String, Object> event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to publish event to topic={} key={}: {}",
                                serviceId, topic, key, ex.getMessage(), ex);
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Envelope builder
    // -----------------------------------------------------------------------

    private Map<String, Object> buildEnvelope(
            String eventId,
            String eventType,
            String aggregateId,
            String aggregateType,
            String causationId,
            Map<String, Object> payload,
            String userId) {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceId", serviceId);
        metadata.put("region",    region);
        if (userId != null) metadata.put("userId", userId);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId",       eventId);
        envelope.put("eventType",     eventType);
        envelope.put("aggregateId",   aggregateId);
        envelope.put("aggregateType", aggregateType);
        envelope.put("version",       SCHEMA_VERSION);
        envelope.put("occurredAt",    Instant.now().toString());
        envelope.put("correlationId", MDC.get("correlationId") != null
                ? MDC.get("correlationId") : UUID.randomUUID().toString());
        envelope.put("metadata",      metadata);
        envelope.put("payload",       payload);
        if (causationId != null) envelope.put("causationId", causationId);
        return envelope;
    }

    // -----------------------------------------------------------------------
    // Header extraction
    // -----------------------------------------------------------------------

    private static String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) return null;
        return new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
