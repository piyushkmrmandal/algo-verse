package com.algoverse.submission.dto;

import java.util.UUID;

/**
 * Kafka event published to {@code algoverse.submission.graded}.
 * Consumed by gamification-service and ai-service.
 */
public record GradedEvent(
        UUID submissionId,
        UUID userId,
        String problemSlug,
        String difficulty,
        String status,
        int executionTimeMs,
        boolean isFirstSolve,
        String gradedAt   // ISO-8601
) {}
