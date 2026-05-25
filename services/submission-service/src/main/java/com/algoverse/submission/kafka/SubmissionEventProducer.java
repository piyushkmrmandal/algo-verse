package com.algoverse.submission.kafka;

import com.algoverse.submission.dto.GradedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes graded submission events to Kafka.
 * Topic: algoverse.submission.graded
 * Key: submissionId (for deterministic partitioning)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionEventProducer {

    private final KafkaTemplate<String, GradedEvent> kafkaTemplate;

    @Value("${submission.topics.graded:algoverse.submission.graded}")
    private String gradedTopic;

    /**
     * Publishes a {@link GradedEvent} to the graded topic.
     * Uses the submissionId as the Kafka message key for ordered delivery.
     *
     * @param event the graded event payload
     */
    public void publishGradedEvent(GradedEvent event) {
        UUID submissionId = event.submissionId();
        log.info("SubmissionEventProducer: publishing graded event submissionId={} userId={} problem={} status={}",
                submissionId, event.userId(), event.problemSlug(), event.status());

        CompletableFuture<SendResult<String, GradedEvent>> future =
                kafkaTemplate.send(gradedTopic, submissionId.toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("SubmissionEventProducer: failed to publish event submissionId={}: {}",
                        submissionId, ex.getMessage(), ex);
            } else {
                log.debug("SubmissionEventProducer: event published submissionId={} partition={} offset={}",
                        submissionId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
