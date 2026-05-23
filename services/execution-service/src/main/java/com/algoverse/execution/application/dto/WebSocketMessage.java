package com.algoverse.execution.application.dto;

import java.util.UUID;

/**
 * Envelope for all WebSocket STOMP messages pushed to the client.
 *
 * <p>Sent to {@code /user/{userId}/topic/submission} via SimpMessagingTemplate.
 *
 * <p>Supported {@code type} values:
 * <ul>
 *   <li>{@code STATUS_UPDATE} — submission lifecycle state change</li>
 *   <li>{@code TEST_RESULT}   — individual test case outcome</li>
 *   <li>{@code COMPLETE}      — judging finished, final verdict</li>
 *   <li>{@code ERROR}         — unexpected system error</li>
 * </ul>
 *
 * @param type         One of the four message types listed above.
 * @param submissionId Submission this message relates to.
 * @param payload      Type-specific payload object (serialized as JSON).
 */
public record WebSocketMessage(
        String type,
        UUID submissionId,
        Object payload
) {

    public static WebSocketMessage statusUpdate(UUID submissionId, Object payload) {
        return new WebSocketMessage("STATUS_UPDATE", submissionId, payload);
    }

    public static WebSocketMessage testResult(UUID submissionId, Object payload) {
        return new WebSocketMessage("TEST_RESULT", submissionId, payload);
    }

    public static WebSocketMessage complete(UUID submissionId, Object payload) {
        return new WebSocketMessage("COMPLETE", submissionId, payload);
    }

    public static WebSocketMessage error(UUID submissionId, Object payload) {
        return new WebSocketMessage("ERROR", submissionId, payload);
    }
}
