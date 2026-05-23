package com.algoverse.auth.infrastructure.kafka;

import com.algoverse.auth.domain.model.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventProducer {

    private static final String TOPIC_USER_REGISTERED = "algoverse.users.registered";
    private static final String EVENT_TYPE_USER_REGISTERED = "USER_REGISTERED";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private Counter userRegisteredCounter;
    private Counter userRegisteredErrorCounter;

    @PostConstruct
    public void initMetrics() {
        userRegisteredCounter = Counter.builder("auth.events.user_registered")
                .description("Number of USER_REGISTERED events published")
                .register(meterRegistry);

        userRegisteredErrorCounter = Counter.builder("auth.events.user_registered.errors")
                .description("Number of USER_REGISTERED event publish failures")
                .register(meterRegistry);
    }

    public void publishUserRegistered(User user) {
        String traceId = MDC.get("traceId");
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", EVENT_TYPE_USER_REGISTERED,
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "occurredAt", Instant.now().toString(),
                "traceId", traceId != null ? traceId : ""
        );

        MDC.put("traceId", traceId != null ? traceId : eventId);
        MDC.put("eventId", eventId);
        MDC.put("userId", user.getId().toString());

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(TOPIC_USER_REGISTERED, user.getId().toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish USER_REGISTERED event for userId={}, eventId={}",
                            user.getId(), eventId, ex);
                    userRegisteredErrorCounter.increment();
                } else {
                    log.info("Published USER_REGISTERED event: userId={}, eventId={}, partition={}, offset={}",
                            user.getId(), eventId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    userRegisteredCounter.increment();
                }
            });

        } catch (Exception e) {
            log.error("Exception publishing USER_REGISTERED event for userId={}", user.getId(), e);
            userRegisteredErrorCounter.increment();
        } finally {
            MDC.remove("eventId");
        }
    }
}
