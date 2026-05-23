package com.algoverse.execution.domain;

import com.algoverse.execution.domain.model.SubmissionStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable value object produced by the async judge flow.
 *
 * <p>Consumed by {@link com.algoverse.execution.kafka.SubmissionEventProducer}
 * to build the {@code submission.judged} Kafka event envelope.
 */
@Getter
@Builder
public class JudgeResult {

    private final SubmissionStatus status;

    /** Wall-clock time of the slowest test case, in milliseconds. */
    private final long runtimeMs;

    /** Peak memory across all test cases, in MB. */
    private final long memoryMb;

    private final int testCasesPassed;
    private final int testCasesTotal;

    /** True when this is the user's first accepted submission for this problem. */
    @Builder.Default
    private final boolean firstAccepted = false;

    /** 0–100 percentile rank against other accepted submissions (optional). */
    @Builder.Default
    private final double runtimePercentile = 0.0;

    /** 0–100 percentile rank by memory usage (optional). */
    @Builder.Default
    private final double memoryPercentile = 0.0;

    /** Total wall-clock time including sandbox overhead, in ms. */
    private final long totalProcessingMs;
}
