package com.algoverse.execution.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Per-test-case execution result for a submission.
 *
 * <p>Maps to {@code submission_test_results} in V1__init_execution.sql.
 * {@code orderIndex} is a transient sort field; DB ordering is by insert order.
 */
@Entity
@Table(name = "submission_test_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    /** True when actualOutput (trimmed) equals expected output (trimmed). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;  // "PASSED" | "FAILED" | "TLE" | "MLE" | "RE" | "CE"

    @Column(name = "actual_output", columnDefinition = "TEXT")
    private String actualOutput;

    /** Execution time in milliseconds. */
    @Column(name = "runtime_ms")
    private Integer runtimeMs;

    /** Peak memory in MB. */
    @Column(name = "memory_mb")
    private Integer memoryMb;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 0-based ordering within the submission; not persisted in this version. */
    @Transient
    private int orderIndex;
}
