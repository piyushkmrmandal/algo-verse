package com.algoverse.notification.kafka;

import com.algoverse.notification.service.FirebasePushService;
import com.algoverse.notification.service.InAppNotificationService;
import com.algoverse.notification.service.NotificationService;
import com.algoverse.notification.service.SesEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-topic Kafka consumer for the notification-service.
 *
 * <p>Subscribes to:
 * <ul>
 *   <li>{@code algoverse.badges.earned}         — badge unlock → email + in-app</li>
 *   <li>{@code algoverse.streaks.updated}        — streak milestone → push + in-app</li>
 *   <li>{@code algoverse.notifications.requested} — generic dispatch → delegate to NotificationService</li>
 * </ul>
 *
 * <h3>Routing Logic</h3>
 * <pre>
 *  badges.earned          → SesEmailService (badge-earned template)
 *                         → InAppNotificationService
 *
 *  streaks.updated
 *    if streak ∈ {7, 30, 100} → FirebasePushService
 *                             → InAppNotificationService
 *    otherwise               → no-op (ack and skip)
 *
 *  notifications.requested → NotificationService.dispatch(channel)
 * </pre>
 *
 * <h3>Retry &amp; DLQ</h3>
 * <p>Spring Kafka {@code DefaultErrorHandler} in {@code KafkaConsumerConfig}:
 * 3 retries, exponential backoff starting at 1s (×2 multiplier).
 * After retry exhaustion, {@code DeadLetterPublishingRecoverer} routes to DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    // -----------------------------------------------------------------------
    // Topics
    // -----------------------------------------------------------------------

    private static final String TOPIC_BADGES_EARNED              = "algoverse.badges.earned";
    private static final String TOPIC_STREAKS_UPDATED            = "algoverse.streaks.updated";
    private static final String TOPIC_NOTIFICATIONS_REQUESTED    = "algoverse.notifications.requested";

    // -----------------------------------------------------------------------
    // Streak milestones that trigger notifications
    // -----------------------------------------------------------------------

    private static final Set<Integer> STREAK_MILESTONES = Set.of(7, 30, 100);

    // -----------------------------------------------------------------------
    // SES Template Names
    // -----------------------------------------------------------------------

    private static final String EMAIL_TEMPLATE_BADGE_EARNED      = "badge-earned";
    private static final String EMAIL_TEMPLATE_STREAK_MILESTONE  = "streak-milestone";

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final SesEmailService sesEmailService;
    private final FirebasePushService firebasePushService;
    private final InAppNotificationService inAppNotificationService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:notification-service}")
    private String serviceId;

    // -----------------------------------------------------------------------
    // Listener — badges.earned
    // -----------------------------------------------------------------------

    /**
     * Handles badge-earned events: dispatches an email (SES) and an in-app
     * notification so the user sees the badge unlock in both their inbox and
     * the notification drawer.
     *
     * @param record raw ConsumerRecord for header access
     * @param ack    manual acknowledgment — committed only on success
     */
    @KafkaListener(
            topics           = TOPIC_BADGES_EARNED,
            groupId          = "notification-service",
            concurrency      = "6",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBadgeEarned(ConsumerRecord<String, String> record, Acknowledgment ack) {
        populateMdc(record);
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload  = envelope.path("payload");

            String eventId          = envelope.path("eventId").asText();
            String userId           = payload.path("userId").asText();
            String badgeName        = payload.path("badgeName").asText();
            String badgeSlug        = payload.path("badgeSlug").asText();
            String badgeDescription = payload.path("badgeDescription").asText();
            String rarity           = payload.path("rarity").asText();
            int    xpReward         = payload.path("xpReward").asInt();
            String iconUrl          = payload.path("iconUrl").asText();

            MDC.put("userId",    userId);
            MDC.put("badgeSlug", badgeSlug);

            log.info("[{}] Processing badge.earned for userId={} badge={}", serviceId, userId, badgeSlug);

            // --- Email notification ---
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("badgeName",        badgeName);
            emailData.put("badgeDescription", badgeDescription);
            emailData.put("rarity",           rarity);
            emailData.put("xpReward",         xpReward);
            emailData.put("iconUrl",          iconUrl);
            emailData.put("badgeSlug",        badgeSlug);

            // Email requires user's address; NotificationService resolves it internally
            // We call SesEmailService with a placeholder address lookup via userId convention;
            // a real implementation resolves the address from the user profile cache.
            sesEmailService.send(
                    resolveEmailAddress(userId),
                    EMAIL_TEMPLATE_BADGE_EARNED,
                    emailData,
                    "badge-earned:" + eventId
            );

            // --- In-app notification ---
            Map<String, Object> inAppData = new HashMap<>();
            inAppData.put("badgeName",   badgeName);
            inAppData.put("badgeSlug",   badgeSlug);
            inAppData.put("rarity",      rarity);
            inAppData.put("iconUrl",     iconUrl);
            inAppData.put("xpReward",    xpReward);

            inAppNotificationService.persist(
                    userId,
                    "badge.earned",
                    "Badge Unlocked: " + badgeName,
                    "You earned the " + badgeName + " badge (" + rarity + ")! +" + xpReward + " XP",
                    inAppData,
                    "badge-earned:inapp:" + eventId
            );

            ack.acknowledge();
            log.info("[{}] Badge notification dispatched userId={} badge={}", serviceId, userId, badgeSlug);

        } catch (Exception e) {
            log.error("[{}] Error processing badge.earned event correlationId={} offset={}: {}",
                    serviceId, MDC.get("correlationId"), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Badge notification failed at offset " + record.offset(), e);
        } finally {
            MDC.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Listener — streaks.updated
    // -----------------------------------------------------------------------

    /**
     * Handles streak-updated events. Only acts on milestone streak values
     * (7, 30, 100 days) to avoid notification fatigue. On milestone:
     * dispatches a Firebase push notification and an in-app notification.
     *
     * @param record raw ConsumerRecord
     * @param ack    manual acknowledgment
     */
    @KafkaListener(
            topics           = TOPIC_STREAKS_UPDATED,
            groupId          = "notification-service",
            concurrency      = "6",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStreakUpdated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        populateMdc(record);
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload  = envelope.path("payload");

            String eventId       = envelope.path("eventId").asText();
            String userId        = payload.path("userId").asText();
            int currentStreak    = payload.path("currentStreak").asInt();
            int longestStreak    = payload.path("longestStreak").asInt();
            boolean streakBroken = payload.path("streakBroken").asBoolean(false);

            MDC.put("userId", userId);
            MDC.put("streak", String.valueOf(currentStreak));

            // Only send notifications on milestone streaks; skip otherwise
            if (!STREAK_MILESTONES.contains(currentStreak)) {
                log.debug("[{}] Streak {} is not a milestone, skipping notification for userId={}",
                        serviceId, currentStreak, userId);
                ack.acknowledge();
                return;
            }

            if (streakBroken) {
                // Streak was just broken — do not celebrate
                ack.acknowledge();
                return;
            }

            log.info("[{}] Streak milestone reached userId={} streak={}",
                    serviceId, userId, currentStreak);

            String pushTitle  = streakPushTitle(currentStreak);
            String pushBody   = streakPushBody(currentStreak);
            String inAppTitle = pushTitle;
            String inAppBody  = streakInAppBody(currentStreak, longestStreak);

            // --- Push notification ---
            Map<String, String> pushData = new HashMap<>();
            pushData.put("streakCount",   String.valueOf(currentStreak));
            pushData.put("streakType",    streakMilestoneLabel(currentStreak));
            pushData.put("navigateTo",    "/profile/streaks");

            firebasePushService.send(
                    userId,
                    pushTitle,
                    pushBody,
                    pushData,
                    "streak-milestone:push:" + eventId
            );

            // --- In-app notification ---
            Map<String, Object> inAppData = new HashMap<>();
            inAppData.put("currentStreak", currentStreak);
            inAppData.put("longestStreak", longestStreak);
            inAppData.put("milestone",     currentStreak);

            inAppNotificationService.persist(
                    userId,
                    "streak.milestone",
                    inAppTitle,
                    inAppBody,
                    inAppData,
                    "streak-milestone:inapp:" + eventId
            );

            ack.acknowledge();
            log.info("[{}] Streak milestone notification dispatched userId={} streak={}",
                    serviceId, userId, currentStreak);

        } catch (Exception e) {
            log.error("[{}] Error processing streak.updated event correlationId={} offset={}: {}",
                    serviceId, MDC.get("correlationId"), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Streak notification failed at offset " + record.offset(), e);
        } finally {
            MDC.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Listener — notifications.requested
    // -----------------------------------------------------------------------

    /**
     * Handles generic notification requests from any upstream service.
     * Delegates channel routing to {@link NotificationService#dispatch} which
     * encapsulates user-preference checks and channel-specific logic.
     *
     * @param record raw ConsumerRecord
     * @param ack    manual acknowledgment
     */
    @KafkaListener(
            topics           = TOPIC_NOTIFICATIONS_REQUESTED,
            groupId          = "notification-service",
            concurrency      = "6",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onNotificationRequested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        populateMdc(record);
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode payload  = envelope.path("payload");

            String eventId  = envelope.path("eventId").asText();
            String userId   = payload.path("userId").asText();
            String channel  = payload.path("channel").asText();
            String type     = payload.path("type").asText();

            MDC.put("userId",  userId);
            MDC.put("channel", channel);
            MDC.put("type",    type);

            // Deserialize the data object into a flat map
            Map<String, Object> data = new HashMap<>();
            payload.path("data").fields()
                    .forEachRemaining(entry -> data.put(entry.getKey(), parseJsonValue(entry.getValue())));

            log.info("[{}] Dispatching notification userId={} channel={} type={} eventId={}",
                    serviceId, userId, channel, type, eventId);

            notificationService.dispatch(userId, channel, type, data, "notification-req:" + eventId);

            ack.acknowledge();
            log.info("[{}] Notification dispatched userId={} channel={} type={}",
                    serviceId, userId, channel, type);

        } catch (Exception e) {
            log.error("[{}] Error processing notification.requested event correlationId={} offset={}: {}",
                    serviceId, MDC.get("correlationId"), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Generic notification failed at offset " + record.offset(), e);
        } finally {
            MDC.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers — streak copy
    // -----------------------------------------------------------------------

    private static String streakPushTitle(int streak) {
        return switch (streak) {
            case 7   -> "🔥 7-Day Streak!";
            case 30  -> "🚀 30-Day Streak!";
            case 100 -> "💯 100-Day Streak!";
            default  -> streak + "-Day Streak!";
        };
    }

    private static String streakPushBody(int streak) {
        return switch (streak) {
            case 7   -> "One week of daily coding! Keep the fire going.";
            case 30  -> "A whole month of daily practice! You're unstoppable.";
            case 100 -> "100 consecutive days of coding! Legendary dedication.";
            default  -> "You've reached a " + streak + "-day coding streak!";
        };
    }

    private static String streakInAppBody(int streak, int longestStreak) {
        String base = streakPushBody(streak);
        if (streak >= longestStreak) {
            base += " This is your longest streak ever!";
        }
        return base;
    }

    private static String streakMilestoneLabel(int streak) {
        return switch (streak) {
            case 7   -> "WEEKLY";
            case 30  -> "MONTHLY";
            case 100 -> "CENTURY";
            default  -> "MILESTONE_" + streak;
        };
    }

    // -----------------------------------------------------------------------
    // Helpers — MDC
    // -----------------------------------------------------------------------

    private void populateMdc(ConsumerRecord<?, ?> record) {
        String correlationId = extractHeader(record, "x-algoverse-correlation-id");
        MDC.put("correlationId",  correlationId != null ? correlationId : UUID.randomUUID().toString());
        MDC.put("kafkaTopic",     record.topic());
        MDC.put("kafkaPartition", String.valueOf(record.partition()));
        MDC.put("kafkaOffset",    String.valueOf(record.offset()));
    }

    private static String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // Helpers — JSON
    // -----------------------------------------------------------------------

    private static Object parseJsonValue(JsonNode node) {
        if (node.isTextual())    return node.asText();
        if (node.isInt())        return node.asInt();
        if (node.isLong())       return node.asLong();
        if (node.isDouble())     return node.asDouble();
        if (node.isBoolean())    return node.asBoolean();
        if (node.isNull())       return null;
        return node.toString();  // nested objects as raw JSON string
    }

    // -----------------------------------------------------------------------
    // Helpers — user profile resolution stub
    // -----------------------------------------------------------------------

    /**
     * Resolves the email address for a given userId.
     *
     * <p>In production this calls the user-profile service gRPC endpoint or
     * reads from a Redis cache populated by auth-service on registration.
     * This stub returns a placeholder; the real implementation is in
     * {@code UserProfileClient}.
     */
    private String resolveEmailAddress(String userId) {
        // TODO: Inject UserProfileClient and call userProfileClient.getEmail(userId)
        return "user+" + userId + "@users.algoverse.io";
    }
}
