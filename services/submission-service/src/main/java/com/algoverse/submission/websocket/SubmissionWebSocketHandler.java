package com.algoverse.submission.websocket;

import com.algoverse.submission.dto.SubmissionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pushes real-time submission results to connected WebSocket clients.
 *
 * <p>Clients subscribe to {@code /topic/submissions/{submissionId}}
 * to receive grading updates for a specific submission.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Sends the final graded result to all subscribers of the submission topic.
     *
     * @param submissionId the UUID of the graded submission
     * @param result       the fully populated grading result
     */
    public void sendResult(UUID submissionId, SubmissionResult result) {
        String destination = "/topic/submissions/" + submissionId;
        log.debug("SubmissionWebSocketHandler: pushing result to {} status={}",
                destination, result.status());
        messagingTemplate.convertAndSend(destination, result);
    }
}
