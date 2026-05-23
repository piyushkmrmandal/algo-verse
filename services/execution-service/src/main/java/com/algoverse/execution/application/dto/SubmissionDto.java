package com.algoverse.execution.application.dto;

import com.algoverse.execution.domain.model.Language;
import com.algoverse.execution.domain.model.SubmissionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * API projection of a {@link com.algoverse.execution.domain.model.Submission}.
 *
 * @param id               Submission UUID.
 * @param userId           Owner UUID.
 * @param problemId        Problem UUID.
 * @param language         Programming language used.
 * @param status           Current lifecycle status.
 * @param runtimeMs        Slowest test case wall-clock time, in ms (null until judged).
 * @param memoryMb         Peak memory across test cases, in MB (null until judged).
 * @param passedTestCases  Number of test cases that passed.
 * @param totalTestCases   Total number of test cases evaluated.
 * @param errorMessage     Compiler or runtime error message (null on success).
 * @param createdAt        Submission receipt timestamp.
 */
public record SubmissionDto(
        UUID id,
        UUID userId,
        UUID problemId,
        Language language,
        SubmissionStatus status,
        Integer runtimeMs,
        Integer memoryMb,
        int passedTestCases,
        int totalTestCases,
        String errorMessage,
        Instant createdAt
) {}
