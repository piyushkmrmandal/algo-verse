package com.algoverse.submission.dto;

import com.algoverse.submission.domain.SubmissionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight response returned immediately after a submission is created,
 * and used in list/history endpoints.
 */
public record SubmissionResponse(
        UUID id,
        String problemSlug,
        String language,
        SubmissionStatus status,
        String difficulty,
        OffsetDateTime submittedAt
) {}
