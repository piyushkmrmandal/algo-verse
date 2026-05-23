package com.algoverse.notification.service;

import java.util.Map;

/**
 * Interface for sending transactional emails via AWS Simple Email Service (SES).
 *
 * <p>Implementations must be idempotent — the same {@code idempotencyKey}
 * passed twice should send the email only once.
 */
public interface SesEmailService {

    /**
     * Sends a templated email to a single recipient.
     *
     * @param toAddress      Recipient email address
     * @param templateName   SES template name (e.g. "badge-earned", "welcome")
     * @param templateData   Key-value pairs matching the SES template placeholders
     * @param idempotencyKey Deduplication key scoped to the caller's domain event ID
     */
    void send(String toAddress, String templateName, Map<String, Object> templateData,
              String idempotencyKey);
}
