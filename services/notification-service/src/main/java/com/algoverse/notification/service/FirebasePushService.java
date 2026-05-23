package com.algoverse.notification.service;

import java.util.Map;

/**
 * Interface for sending push notifications via Firebase Cloud Messaging (FCM).
 *
 * <p>Implementations must handle device-token lookup internally — callers
 * provide only the {@code userId}. If the user has no registered FCM token
 * the send is silently skipped.
 */
public interface FirebasePushService {

    /**
     * Sends a push notification to all registered devices of the given user.
     *
     * @param userId     The AlgoVerse user identifier
     * @param title      Notification title (max 65 chars)
     * @param body       Notification body text (max 240 chars)
     * @param data       Optional key-value data payload for the client app
     * @param idempotencyKey Deduplication key scoped to the caller's domain event ID
     */
    void send(String userId, String title, String body, Map<String, String> data,
              String idempotencyKey);
}
