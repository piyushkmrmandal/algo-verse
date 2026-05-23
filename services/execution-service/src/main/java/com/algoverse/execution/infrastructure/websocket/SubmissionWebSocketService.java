package com.algoverse.execution.infrastructure.websocket;

import com.algoverse.execution.application.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin wrapper around {@link SimpMessagingTemplate} for pushing real-time
 * submission updates to the connected browser client.
 *
 * <p>Messages are sent to the per-user destination
 * {@code /user/{userId}/topic/submission}. The Spring STOMP broker resolves
 * the target session from the principal name set during WebSocket handshake.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionWebSocketService {

    private static final String SUBMISSION_DESTINATION = "/topic/submission";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Pushes {@code msg} to the authenticated user's subscription.
     *
     * @param userId Recipient user UUID — matched against the STOMP principal.
     * @param msg    The message envelope to deliver.
     */
    public void sendSubmissionUpdate(UUID userId, WebSocketMessage msg) {
        String destination = SUBMISSION_DESTINATION;
        log.debug("Sending WebSocket message type={} submissionId={} to userId={}",
                msg.type(), msg.submissionId(), userId);
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), destination, msg);
        } catch (Exception e) {
            // Non-fatal: client may have disconnected. Log and continue judging.
            log.warn("Failed to send WebSocket message to userId={} submissionId={}: {}",
                    userId, msg.submissionId(), e.getMessage());
        }
    }
}
