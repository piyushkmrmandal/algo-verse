package com.algoverse.gamification.kafka;

import com.algoverse.gamification.badge.BadgeService;
import com.algoverse.gamification.badge.domain.BadgeEarnedResult;
import com.algoverse.gamification.streak.StreakService;
import com.algoverse.gamification.streak.domain.StreakResult;
import com.algoverse.gamification.xp.XpService;
import com.algoverse.gamification.xp.domain.XpResult;
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

import java.time.Duration;
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
 *
 * <h3>XP Calculation Rules</h3>
 * <ul>
 *   <li>EASY   = 10 base XP</li>
 *   <li>MEDIUM = 25 base XP</li>
 *   <li>HARD   = 50 base XP</li>
 *   <li>First solve bonus: 2× multiplier on base XP</li>
 *   <li>Streak bonus (streak &gt; 7 days): +20% on top of multiplied XP</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionJudgedConsumer {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final String TOPIC_SUBMISSIONS_JUDGED      = "algoverse.submissions.judged";
    private static final String TOPIC_XP_UPDATED              = "algoverse.users.xp-updated";
    private static final String TOPIC_STREAKS_UPDATED         = "algoverse.streaks.updated";
    private static final String TOPIC_BADGES_EARNED           = "algoverse.badges.earned";
    private static final String TOPIC_PROBLEMS_SOLVED_1ST     = "algoverse.problems.solved-first-time";

    private static final String GROUP_ID = "gamification-service";

    /** Redis key prefix for idempotency deduplication */
    private static final String REDIS_IDEMPOTENCY_PREFIX = "gami:processed:submission:";

    /** TTL for the Redis idempotency key — 24 hours */
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // XP values per difficulty
    private static final int XP_EASY   = 10;
    private static final int XP_MEDIUM = 25;
    private static final int XP_HARD   = 50;

    // Bonus multipliers / additions
    private static final double FIRST_SOLVE_MULTIPLIER = 2.0;
    private static final double STREAK_BONUS_PERCENT   = 0.20;
    private static final int    STREAK_BONUS_THRESHOLD = 7;

    private static final int SCHEMA_VERSION_XP_UPDATED    = 1;
    private static final int SCHEMA_VERSION_STREAK        = 1;
    private static final int SCHEMA_VERSION_BADGE         = 1;
    private static final int SCHEMA_VERSION_FIRST_SOLVE   = 1;

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

    /**
     * Main consumer method. Called by Spring Kafka on every message from
     * {@code algoverse.submissions.judged}.
     *
     * <p>Offset is committed manually ({@link Acknowledgment#acknowledge()}) only
     * after all downstream state has been written and events published. This
     * provides at-least-once delivery semantics; idempotency guard prevents
     * double-processing on redelivery.
     *
     * @param record raw ConsumerRecord for header and offset access
     * @param ack    manual acknowledgment handle
     */
    @KafkaListener(
            topics      = TOPIC_SUBMISSIONS_JUDGED,
            groupId     = GROUP_ID,
            concurrency = "12",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubmissionJudged(ConsumerRecord<String, String> record, Acknowledgment ack) {

        String correlationId = extractHeader(record, "x-algoverse-correlation-id");
        MDC.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        MDC.put("kafkaTopic",    record.topic());
        MDC.put("kafkaPartition", String.valueOf(record.partition()));
        MDC.put("kafkaOffset",    String.valueOf(record.offset()));

        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload  = envelope.path("payload");

            String submissionId   = payload.path("submissionId").asText();
            String userId         = payload.path("userId").asText();
            String problemId      = payload.path("problemId").asText();
            String problemSlug    = payload.path("problemSlug").asText();
            String status         = payload.path("status").asText();
            String difficulty     = payload.path("difficulty").asText();
            boolean isFirstAccepted = payload.path("isFirstAccepted").asBoolean(false);
            long runtimeMs        = payload.path("runtimeMs").asLong();
            double memoryMb       = payload.path("memoryMb").asDouble();
            int testCasesPassed   = payload.path("testCasesPassed").asInt();
            int testCasesTotal    = payload.path("testCasesTotal").asInt();
            String language       = payload.path("language").asText();
            String causationId    = envelope.path("eventId").asText();

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

            // ----------------------------------------------------------------
            // Step 1: Calculate XP
            // ----------------------------------------------------------------
            int baseXp    = baseXpForDifficulty(difficulty);
            int awardedXp = applyBonuses(baseXp, isFirstAccepted, userId);

            // ----------------------------------------------------------------
            // Step 2: Update XP via XpService
            // ----------------------------------------------------------------
            XpResult xpResult = xpService.awardXp(
                    userId,
                    awardedXp,
                    isFirstAccepted ? "FIRST_SOLVE_BONUS" : "SUBMISSION_ACCEPTED",
                    submissionId
            );

            log.info("[{}] XP awarded userId={} deltaXp={} newTotalXp={} newLevel={}",
                    serviceId, userId, awardedXp, xpResult.getNewTotalXp(), xpResult.getNewLevel());

            // ----------------------------------------------------------------
            // Step 3: Update streak via StreakService
            // ----------------------------------------------------------------
            StreakResult streakResult = streakService.recordSolveForToday(userId);

            log.info("[{}] Streak updated userId={} currentStreak={} longestStreak={}",
                    serviceId, userId, streakResult.getCurrentStreak(), streakResult.getLongestStreak());

            // ----------------------------------------------------------------
            // Step 4: Badge check
            // ----------------------------------------------------------------
            List<BadgeEarnedResult> earnedBadges = badgeService.evaluate(
                    userId,
                    submissionId,
                    problemId,
                    difficulty,
                    isFirstAccepted,
                    streakResult.getCurrentStreak(),
                    xpResult.getNewTotalXp(),
                    xpResult.getNewLevel()
            );

            // ----------------------------------------------------------------
            // Step 5: Publish downstream events
            // ----------------------------------------------------------------
            publishXpUpdatedEvent(userId, awardedXp, xpResult, submissionId, causationId);
            publishStreakUpdatedEvent(userId, streakResult, causationId);

            for (BadgeEarnedResult badge : earnedBadges) {
                publishBadgeEarnedEvent(userId, badge, causationId);
                log.info("[{}] Badge earned userId={} badgeSlug={} rarity={}",
                        serviceId, userId, badge.getBadgeSlug(), badge.getRarity());
            }

            if (isFirstAccepted) {
                publishProblemSolvedFirstTimeEvent(
                        userId, problemId, problemSlug, difficulty,
                        language, runtimeMs, memoryMb, submissionId,
                        xpResult.getTotalSolvedCount(), causationId
                );
            }

            // ----------------------------------------------------------------
            // Finalize
            // ----------------------------------------------------------------
            markAsProcessed(submissionId);
            ack.acknowledge();

            log.info("[{}] Gamification pipeline complete for submissionId={} userId={} "
                            + "xpAwarded={} streak={} badgesEarned={}",
                    serviceId, submissionId, userId, awardedXp,
                    streakResult.getCurrentStreak(), earnedBadges.size());

        } catch (Exception e) {
            // Do NOT ack — Spring Kafka DefaultErrorHandler will retry.
            // After 3 retries, DeadLetterPublishingRecoverer sends to DLQ.
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
    // XP Calculation
    // -----------------------------------------------------------------------

    private int baseXpForDifficulty(String difficulty) {
        return switch (difficulty.toUpperCase()) {
            case "EASY"   -> XP_EASY;
            case "MEDIUM" -> XP_MEDIUM;
            case "HARD"   -> XP_HARD;
            default -> {
                log.warn("[{}] Unknown difficulty '{}', defaulting to EASY XP", serviceId, difficulty);
                yield XP_EASY;
            }
        };
    }

    /**
     * Applies the first-solve multiplier and streak bonus (if applicable) to
     * the base XP value. Order matters: first-solve multiplier is applied to
     * base, then streak bonus is applied to the multiplied total.
     *
     * <pre>
     * finalXp = baseXp
     *           × (isFirstAccepted ? 2.0 : 1.0)
     *           × (currentStreak > 7 ? 1.20 : 1.0)
     * </pre>
     */
    private int applyBonuses(int baseXp, boolean isFirstAccepted, String userId) {
        double xp = baseXp;

        if (isFirstAccepted) {
            xp *= FIRST_SOLVE_MULTIPLIER;
            log.debug("[{}] First-solve multiplier applied userId={}: {}→{}",
                    serviceId, userId, baseXp, (int) xp);
        }

        // Fetch current streak to determine streak bonus eligibility.
        // We read the current streak synchronously here because we need it
        // before StreakService.recordSolveForToday() is called.
        int currentStreak = streakService.getCurrentStreak(userId);
        if (currentStreak > STREAK_BONUS_THRESHOLD) {
            xp *= (1.0 + STREAK_BONUS_PERCENT);
            log.debug("[{}] Streak bonus applied userId={} streak={}: xp={}",
                    serviceId, userId, currentStreak, (int) xp);
        }

        return (int) Math.round(xp);
    }

    // -----------------------------------------------------------------------
    // Idempotency (Redis)
    // -----------------------------------------------------------------------

    private boolean isAlreadyProcessed(String submissionId) {
        String key = REDIS_IDEMPOTENCY_PREFIX + submissionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markAsProcessed(String submissionId) {
        String key = REDIS_IDEMPOTENCY_PREFIX + submissionId;
        redisTemplate.opsForValue().set(key, "1", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
    }

    // -----------------------------------------------------------------------
    // Downstream event publishers
    // -----------------------------------------------------------------------

    private void publishXpUpdatedEvent(
            String userId,
            int deltaXp,
            XpResult xpResult,
            String submissionId,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",         userId);
        payload.put("deltaXp",        deltaXp);
        payload.put("newTotalXp",     xpResult.getNewTotalXp());
        payload.put("newLevel",       xpResult.getNewLevel());
        payload.put("previousLevel",  xpResult.getPreviousLevel());
        payload.put("isLevelUp",      xpResult.isLevelUp());
        payload.put("source",         xpResult.getSource());
        payload.put("referenceId",    submissionId);

        Map<String, Object> event = buildEnvelope(
                UUID.randomUUID().toString(),
                "user.xp-updated",
                userId,
                "User",
                SCHEMA_VERSION_XP_UPDATED,
                MDC.get("correlationId"),
                causationId,
                payload,
                userId
        );

        kafkaTemplate.send(TOPIC_XP_UPDATED, userId, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to publish XpUpdatedEvent for userId={}: {}",
                                serviceId, userId, ex.getMessage(), ex);
                    }
                });
    }

    private void publishStreakUpdatedEvent(
            String userId,
            StreakResult streakResult,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",          userId);
        payload.put("currentStreak",   streakResult.getCurrentStreak());
        payload.put("longestStreak",   streakResult.getLongestStreak());
        payload.put("date",            streakResult.getDate().toString());
        payload.put("streakBroken",    streakResult.isStreakBroken());
        payload.put("previousStreak",  streakResult.getPreviousStreak());

        Map<String, Object> event = buildEnvelope(
                UUID.randomUUID().toString(),
                "streak.updated",
                userId,
                "User",
                SCHEMA_VERSION_STREAK,
                MDC.get("correlationId"),
                causationId,
                payload,
                userId
        );

        kafkaTemplate.send(TOPIC_STREAKS_UPDATED, userId, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to publish StreakUpdatedEvent for userId={}: {}",
                                serviceId, userId, ex.getMessage(), ex);
                    }
                });
    }

    private void publishBadgeEarnedEvent(
            String userId,
            BadgeEarnedResult badge,
            String causationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",           userId);
        payload.put("badgeId",          badge.getBadgeId());
        payload.put("badgeSlug",        badge.getBadgeSlug());
        payload.put("badgeName",        badge.getBadgeName());
        payload.put("badgeDescription", badge.getBadgeDescription());
        payload.put("rarity",           badge.getRarity());
        payload.put("xpReward",         badge.getXpReward());
        payload.put("iconUrl",          badge.getIconUrl());

        Map<String, Object> event = buildEnvelope(
                UUID.randomUUID().toString(),
                "badge.earned",
                badge.getBadgeId(),
                "Badge",
                SCHEMA_VERSION_BADGE,
                MDC.get("correlationId"),
                causationId,
                payload,
                userId
        );

        kafkaTemplate.send(TOPIC_BADGES_EARNED, userId, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to publish BadgeEarnedEvent for userId={} badgeSlug={}: {}",
                                serviceId, userId, badge.getBadgeSlug(), ex.getMessage(), ex);
                    }
                });
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
        payload.put("attemptCount",     xpService.getAttemptCount(userId, problemId));

        Map<String, Object> event = buildEnvelope(
                UUID.randomUUID().toString(),
                "problem.solved-first-time",
                problemId,
                "Problem",
                SCHEMA_VERSION_FIRST_SOLVE,
                MDC.get("correlationId"),
                causationId,
                payload,
                userId
        );

        kafkaTemplate.send(TOPIC_PROBLEMS_SOLVED_1ST, userId, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to publish ProblemSolvedFirstTimeEvent for userId={} problemId={}: {}",
                                serviceId, userId, problemId, ex.getMessage(), ex);
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
            int version,
            String correlationId,
            String causationId,
            Map<String, Object> payload,
            String userId) {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceId", serviceId);
        metadata.put("region",    region);
        if (userId != null) {
            metadata.put("userId", userId);
        }

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId",       eventId);
        envelope.put("eventType",     eventType);
        envelope.put("aggregateId",   aggregateId);
        envelope.put("aggregateType", aggregateType);
        envelope.put("version",       version);
        envelope.put("occurredAt",    Instant.now().toString());
        envelope.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        envelope.put("metadata",      metadata);
        envelope.put("payload",       payload);
        if (causationId != null) {
            envelope.put("causationId", causationId);
        }
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
