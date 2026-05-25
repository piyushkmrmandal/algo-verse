package com.algoverse.submission.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity representing a code submission.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_slug", nullable = false, length = 255)
    private String problemSlug;

    @Column(name = "language", nullable = false, length = 20)
    private String language;

    @Column(name = "code", nullable = false, columnDefinition = "TEXT")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(name = "difficulty", nullable = false, length = 20)
    @Builder.Default
    private String difficulty = "MEDIUM";

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_used_kb")
    private Integer memoryUsedKb;

    @Column(name = "passed_test_cases")
    @Builder.Default
    private Integer passedTestCases = 0;

    @Column(name = "total_test_cases")
    @Builder.Default
    private Integer totalTestCases = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "is_first_solve")
    @Builder.Default
    private Boolean isFirstSolve = false;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;
}
