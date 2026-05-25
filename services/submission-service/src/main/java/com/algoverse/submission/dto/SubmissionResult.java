package com.algoverse.submission.dto;

import com.algoverse.submission.domain.SubmissionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full graded result, returned by GET /api/v1/submissions/{id}
 * and pushed over WebSocket when grading completes.
 */
public record SubmissionResult(
        UUID id,
        UUID userId,
        String problemSlug,
        String language,
        SubmissionStatus status,
        String difficulty,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        Integer passedTestCases,
        Integer totalTestCases,
        String errorMessage,
        Boolean isFirstSolve,
        OffsetDateTime submittedAt,
        OffsetDateTime gradedAt
) {}
