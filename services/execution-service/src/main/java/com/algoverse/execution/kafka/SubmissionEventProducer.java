package com.algoverse.execution.kafka;

import com.algoverse.execution.domain.JudgeResult;
import com.algoverse.execution.domain.model.Submission;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for submission lifecycle events.
 *
 * <p>Produces to:
 * <ul>
 *   <li>{@code algoverse.submissions.created} — when a submission is queued</li>
 *   <li>{@code algoverse.submissions.judged}  — when judge returns a verdict</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>JSON serialization via Spring Kafka JsonSerializer</li>
 *   <li>MDC-based traceId / correlationId propagation in Kafka headers</li>
 *   <li>Micrometer metrics: submissions.produced.total, submissions.produced.errors</li>
 *   <li>Resilience4j CircuitBreaker wrapping every send call</li>
 *   <li>DLQ fallback when circuit is OPEN or send fails after retries</li>
 * </ul>
 */
@Slf4j
@Component
public class SubmissionEventProducer {

    // -----------------------------------------------------------------------
    // Topic constants
    // -----------------------------------------------------------------------

    private static final String TOPIC_SUBMISSIONS_CREATED = "algoverse.submissions.created";
    private static final String TOPIC_SUBMISSIONS_JUDGED  = "algoverse.submissions.judged";
    private static final String TOPIC_SUBMISSIONS_CREATED_DLQ = "algoverse.submissions.created.dlq";
    private static final String TOPIC_SUBMISSIONS_JUDGED_DLQ  = "algoverse.submissions.judged.dlq";

    // -----------------------------------------------------------------------
    // Header name constants
    // -----------------------------------------------------------------------

    private static final String HEADER_CORRELATION_ID  = "x-algoverse-correlation-id";
    private static final String HEADER_CAUSATION_ID    = "x-algoverse-causation-id";
    private static final String HEADER_EVENT_TYPE      = "x-algoverse-event-type";
    private static final String HEADER_SERVICE_ID      = "x-algoverse-service-id";
    private static final String HEADER_TRACE_ID        = "x-b3-traceid";
    private static final String HEADER_SPAN_ID         = "x-b3-spanid";

    // -----------------------------------------------------------------------
    // Event type identifiers
    // -----------------------------------------------------------------------

    private static final String EVENT_TYPE_SUBMISSION_CREATED = "submission.created";
    private static final String EVENT_TYPE_SUBMISSION_JUDGED  = "submission.judged";

    // -----------------------------------------------------------------------
    // Schema versions
    // -----------------------------------------------------------------------

    private static final int SCHEMA_VERSION_SUBMISSION_CREATED = 1;
    private static final int SCHEMA_VERSION_SUBMISSION_JUDGED  = 1;

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;

    @Value("${spring.application.name:execution-service}")
    private String serviceId;

    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;

    // Micrometer counters — initialized lazily in @PostConstruct so the
    // MeterRegistry is fully configured before we register them.
    private Counter producedTotalCounter;
    private Counter producedErrorsCounter;
    private Counter dlqFallbackCounter;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public SubmissionEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry) {

        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka-producer");
        this.meterRegistry  = meterRegistry;
    }

    @PostConstruct
    private void initMetrics() {
        producedTotalCounter = Counter.builder("submissions.produced.total")
                .description("Total number of submission events successfully published to Kafka")
                .tag("service", serviceId)
                .register(meterRegistry);

        producedErrorsCounter = Counter.builder("submissions.produced.errors")
                .description("Total number of submission event publish failures")
                .tag("service", serviceId)
                .register(meterRegistry);

        dlqFallbackCounter = Counter.builder("submissions.produced.dlq.fallback")
                .description("Total number of events routed to DLQ after circuit open or exhausted retries")
                .tag("service", serviceId)
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Publishes a {@code submission.created} event to Kafka.
     *
     * <p>Partition key: {@code submission.userId} — guarantees ordering for all
     * submissions from the same user within the same partition.
     *
     * @param submission the freshly persisted Submission domain object
     * @param queuePosition 1-based position in the execution queue
     */
    public void sendSubmissionCreated(Submission submission, int queuePosition) {
        String eventId       = UUID.randomUUID().toString();
        String correlationId = resolveCorrelationId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("submissionId",         submission.getId());
        payload.put("userId",               submission.getUserId());
        payload.put("problemId",            submission.getProblemId());
        payload.put("problemSlug",          submission.getProblemSlug());
        payload.put("language",             submission.getLanguage().name());
        payload.put("codeSizeBytes",        submission.getCodeSizeBytes());
        payload.put("queuePosition",        queuePosition);
        payload.put("estimatedWaitSeconds", estimateWaitSeconds(queuePosition));

        Map<String, Object> event = buildEnvelope(
                eventId,
                EVENT_TYPE_SUBMISSION_CREATED,
                submission.getId(),
                "Submission",
                SCHEMA_VERSION_SUBMISSION_CREATED,
                correlationId,
                null,
                payload,
                submission.getUserId()
        );

        ProducerRecord<String, Object> record = buildRecord(
                TOPIC_SUBMISSIONS_CREATED,
                submission.getUserId(),   // partition key
                event,
                eventId,
                correlationId,
                null,
                EVENT_TYPE_SUBMISSION_CREATED
        );

        executeWithCircuitBreaker(
                record,
                TOPIC_SUBMISSIONS_CREATED_DLQ,
                "SubmissionCreated",
                submission.getId()
        );
    }

    /**
     * Publishes a {@code submission.judged} event to Kafka.
     *
     * <p>Partition key: {@code submissionId} — downstream consumers (gamification,
     * analytics, leaderboard) key on submissionId to avoid duplicate processing.
     *
     * @param submission the original Submission domain object
     * @param result     the JudgeResult returned by the sandbox
     */
    public void sendSubmissionJudged(Submission submission, JudgeResult result) {
        String eventId         = UUID.randomUUID().toString();
        String correlationId   = resolveCorrelationId();
        String causationId     = submission.getCreatedEventId(); // links back to submission.created

        Map<String, Object> payload = new HashMap<>();
        payload.put("submissionId",         submission.getId());
        payload.put("userId",               submission.getUserId());
        payload.put("problemId",            submission.getProblemId());
        payload.put("problemSlug",          submission.getProblemSlug());
        payload.put("status",               result.getStatus().name());
        payload.put("runtimeMs",            result.getRuntimeMs());
        payload.put("memoryMb",             result.getMemoryMb());
        payload.put("testCasesPassed",      result.getTestCasesPassed());
        payload.put("testCasesTotal",       result.getTestCasesTotal());
        payload.put("language",             submission.getLanguage().name());
        payload.put("difficulty",           submission.getDifficulty().name());
        payload.put("isFirstAccepted",      result.isFirstAccepted());
        payload.put("runtimePercentile",    result.getRuntimePercentile());
        payload.put("memoryPercentile",     result.getMemoryPercentile());
        payload.put("totalProcessingMs",    result.getTotalProcessingMs());

        Map<String, Object> event = buildEnvelope(
                eventId,
                EVENT_TYPE_SUBMISSION_JUDGED,
                submission.getId(),
                "Submission",
                SCHEMA_VERSION_SUBMISSION_JUDGED,
                correlationId,
                causationId,
                payload,
                submission.getUserId()
        );

        ProducerRecord<String, Object> record = buildRecord(
                TOPIC_SUBMISSIONS_JUDGED,
                submission.getId(),       // partition key
                event,
                eventId,
                correlationId,
                causationId,
                EVENT_TYPE_SUBMISSION_JUDGED
        );

        executeWithCircuitBreaker(
                record,
                TOPIC_SUBMISSIONS_JUDGED_DLQ,
                "SubmissionJudged",
                submission.getId()
        );
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Wraps the Kafka send in a Resilience4j CircuitBreaker. On success the
     * {@code producedTotalCounter} is incremented. On failure (including open
     * circuit) the error counter is incremented and a DLQ fallback is attempted.
     */
    private void executeWithCircuitBreaker(
            ProducerRecord<String, Object> record,
            String dlqTopic,
            String eventLabel,
            String aggregateId) {

        try {
            circuitBreaker.executeCallable(() -> {
                CompletableFuture<SendResult<String, Object>> future =
                        kafkaTemplate.send(record);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to publish {} event for aggregateId={} correlationId={}",
                                serviceId, eventLabel, aggregateId,
                                resolveCorrelationId(), ex);
                        producedErrorsCounter.increment();
                        sendToDlq(record, dlqTopic, ex);
                    } else {
                        log.info("[{}] Published {} event for aggregateId={} "
                                        + "topic={} partition={} offset={} correlationId={}",
                                serviceId, eventLabel, aggregateId,
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                resolveCorrelationId());
                        producedTotalCounter.increment();
                    }
                });
                return null;
            });

        } catch (Exception circuitOpenEx) {
            log.error("[{}] CircuitBreaker OPEN — routing {} event for aggregateId={} to DLQ. Reason: {}",
                    serviceId, eventLabel, aggregateId, circuitOpenEx.getMessage());
            producedErrorsCounter.increment();
            dlqFallbackCounter.increment();
            sendToDlq(record, dlqTopic, circuitOpenEx);
        }
    }

    /**
     * Sends a failed message directly to the DLQ topic with failure headers.
     * Best-effort — if this also fails it is logged but not re-thrown to
     * prevent cascading failures in the calling thread.
     */
    private void sendToDlq(
            ProducerRecord<String, Object> originalRecord,
            String dlqTopic,
            Throwable cause) {

        try {
            ProducerRecord<String, Object> dlqRecord =
                    new ProducerRecord<>(dlqTopic, originalRecord.partition(),
                            originalRecord.key(), originalRecord.value());

            // Copy all original headers
            originalRecord.headers().forEach(h -> dlqRecord.headers().add(h));

            // Append failure metadata headers
            addHeader(dlqRecord, "x-algoverse-original-topic",
                    originalRecord.topic());
            addHeader(dlqRecord, "x-algoverse-failure-reason",
                    cause.getClass().getName());
            addHeader(dlqRecord, "x-algoverse-failure-message",
                    truncate(cause.getMessage(), 500));
            addHeader(dlqRecord, "x-algoverse-failed-at",
                    Instant.now().toString());
            addHeader(dlqRecord, "x-algoverse-retry-count", "0"); // producer-side, 0 retries

            kafkaTemplate.send(dlqRecord).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("[{}] CRITICAL: Failed to send to DLQ topic={}. Original message lost for key={}",
                            serviceId, dlqTopic, originalRecord.key(), ex);
                } else {
                    log.warn("[{}] Message routed to DLQ topic={} key={} partition={} offset={}",
                            serviceId, dlqTopic, originalRecord.key(),
                            r.getRecordMetadata().partition(),
                            r.getRecordMetadata().offset());
                    dlqFallbackCounter.increment();
                }
            });

        } catch (Exception dlqEx) {
            log.error("[{}] CRITICAL: Exception while sending to DLQ topic={}: {}",
                    serviceId, dlqTopic, dlqEx.getMessage(), dlqEx);
        }
    }

    /**
     * Builds the standard KafkaEvent envelope map. The map is serialized to JSON
     * by Spring Kafka's JsonSerializer on the way out.
     */
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
        envelope.put("correlationId", correlationId);
        envelope.put("metadata",      metadata);
        envelope.put("payload",       payload);
        if (causationId != null) {
            envelope.put("causationId", causationId);
        }
        return envelope;
    }

    /**
     * Builds a {@link ProducerRecord} with the standard AlgoVerse headers set
     * from the envelope data and MDC context.
     */
    private ProducerRecord<String, Object> buildRecord(
            String topic,
            String partitionKey,
            Map<String, Object> event,
            String eventId,
            String correlationId,
            String causationId,
            String eventType) {

        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, partitionKey, event);

        // Standard propagation headers
        addHeader(record, HEADER_CORRELATION_ID, correlationId);
        addHeader(record, HEADER_EVENT_TYPE,     eventType);
        addHeader(record, HEADER_SERVICE_ID,     serviceId);

        if (causationId != null) {
            addHeader(record, HEADER_CAUSATION_ID, causationId);
        }

        // Distributed tracing (B3 / OpenTelemetry)
        String traceId = MDC.get("traceId");
        String spanId  = MDC.get("spanId");
        if (traceId != null) addHeader(record, HEADER_TRACE_ID, traceId);
        if (spanId  != null) addHeader(record, HEADER_SPAN_ID,  spanId);

        return record;
    }

    /**
     * Resolves the correlation ID from MDC context (set by the HTTP request filter)
     * or generates a fresh UUID as fallback.
     */
    private String resolveCorrelationId() {
        String fromMdc = MDC.get("correlationId");
        return (fromMdc != null && !fromMdc.isBlank())
                ? fromMdc
                : UUID.randomUUID().toString();
    }

    private static void addHeader(ProducerRecord<?, ?> record, String key, String value) {
        if (value != null) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /**
     * Rough heuristic: each submission in queue takes ~2 seconds on average.
     */
    private static int estimateWaitSeconds(int queuePosition) {
        return Math.max(1, queuePosition * 2);
    }
}
