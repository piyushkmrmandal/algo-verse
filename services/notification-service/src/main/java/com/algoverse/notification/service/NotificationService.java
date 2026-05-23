package com.algoverse.notification.service;

import java.util.Map;

/**
 * Top-level notification dispatch service. Routes a notification request to
 * the correct channel(s) based on the requested channel and user preferences.
 *
 * <p>Implementations are expected to:
 * <ul>
 *   <li>Look up the user's email address / FCM tokens from the user profile service</li>
 *   <li>Respect per-channel opt-out preferences</li>
 *   <li>Apply idempotency using the provided key</li>
 * </ul>
 */
public interface NotificationService {

    /**
     * Dispatches a notification to a single channel for the given user.
     *
     * @param userId         Recipient user ID
     * @param channel        Delivery channel: EMAIL, PUSH, or IN_APP
     * @param type           Logical notification type, e.g. "badge.earned"
     * @param data           Template/payload data for rendering the notification
     * @param idempotencyKey Deduplication key
     */
    void dispatch(String userId, String channel, String type,
                  Map<String, Object> data, String idempotencyKey);
}
