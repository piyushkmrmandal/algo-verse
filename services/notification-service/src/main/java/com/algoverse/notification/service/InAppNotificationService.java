package com.algoverse.notification.service;

import java.util.Map;

/**
 * Interface for persisting in-app notifications that are surfaced in the
 * AlgoVerse notification bell / notification drawer.
 *
 * <p>Persisted notifications remain readable until the user dismisses them.
 * Implementations must be idempotent on {@code idempotencyKey}.
 */
public interface InAppNotificationService {

    /**
     * Persists an in-app notification for the given user.
     *
     * @param userId         Recipient user ID
     * @param type           Notification type discriminant, e.g. "badge.earned",
     *                       "streak.milestone", "room.invite"
     * @param title          Short notification title (max 80 chars)
     * @param body           Notification body / detail text (max 500 chars)
     * @param data           Structured data for client rendering
     *                       (e.g. badgeIconUrl, streakCount)
     * @param idempotencyKey Deduplication key — second call with same key is a no-op
     */
    void persist(String userId, String type, String title, String body,
                 Map<String, Object> data, String idempotencyKey);
}
