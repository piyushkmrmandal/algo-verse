package com.algoverse.problem.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemSolvedConsumer {

    private static final String TOPIC = "algoverse.problems.solved-first-time";
    private static final String CACHE_NAME = "problems";

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onProblemSolvedFirstTime(@Payload String message, Acknowledgment acknowledgment) {
        log.debug("Received message from topic {}: {}", TOPIC, message);
        try {
            JsonNode node = objectMapper.readTree(message);

            // Extract problemSlug from the event payload to invalidate specific cache entry
            String problemSlug = null;
            if (node.has("problemSlug")) {
                problemSlug = node.get("problemSlug").asText();
            }

            if (problemSlug != null && !problemSlug.isBlank()) {
                evictProblemCache(problemSlug);
            } else {
                // If no specific slug provided, evict all entries
                evictAllProblemsCache();
            }

            acknowledgment.acknowledge();
            log.info("Cache invalidated for solved-first-time event, problemSlug={}", problemSlug);

        } catch (Exception e) {
            log.error("Error processing solved-first-time event: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to avoid poison pill blocking
        }
    }

    private void evictProblemCache(String slug) {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(slug);
            log.debug("Evicted cache entry for problem slug: {}", slug);
        }
    }

    private void evictAllProblemsCache() {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared all entries from problems cache");
        }
    }
}
