package com.algoverse.execution.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Core aggregate root representing a single code submission.
 *
 * <p>Maps to the {@code submissions} table defined in V1__init_execution.sql.
 * The DB uses a PostgreSQL ENUM for {@code status}; we map it as a VARCHAR
 * to avoid Hibernate/Flyway ENUM sync issues across environments.
 */
@Entity
@Table(name = "submissions")
@EntityListeners(AuditingEntityListener.class)
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

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    /**
     * Human-readable problem slug — denormalized here to avoid cross-service
     * lookups in Kafka events. Populated from ProblemMetaDto at submit time.
     */
    @Column(name = "problem_slug", length = 200)
    private String problemSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 50)
    private Language language;

    @Column(name = "code", nullable = false, columnDefinition = "TEXT")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    /** Wall-clock execution time across all test cases (worst case), in ms. */
    @Column(name = "runtime_ms")
    private Integer runtimeMs;

    /** Peak memory consumed by the sandbox, in MB. */
    @Column(name = "memory_mb")
    private Integer memoryMb;

    @Column(name = "test_cases_passed", nullable = false)
    @Builder.Default
    private int testCasesPassed = 0;

    @Column(name = "test_cases_total", nullable = false)
    @Builder.Default
    private int testCasesTotal = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Problem difficulty — denormalized from problem-service for Kafka events
     * (leaderboard, gamification consumers need it without a round-trip).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    @Builder.Default
    private Difficulty difficulty = Difficulty.MEDIUM;

    /**
     * The event ID of the {@code submission.created} Kafka event.
     * Stored so the {@code submission.judged} event can set {@code causationId}.
     */
    @Column(name = "created_event_id", length = 36)
    private String createdEventId;

    @CreatedDate
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @LastModifiedDate
    @Column(name = "judged_at")
    private Instant judgedAt;

    // -----------------------------------------------------------------------
    // Derived / convenience
    // -----------------------------------------------------------------------

    /** Returns the byte length of the submitted code. */
    public int getCodeSizeBytes() {
        return code == null ? 0 : code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** Convenience accessor matching the producer's expected method name. */
    public String getId() {
        return id == null ? null : id.toString();
    }

    /** Returns userId as String for Kafka partition key. */
    public String getUserId() {
        return userId == null ? null : userId.toString();
    }

    /** Returns problemId as String. */
    public String getProblemId() {
        return problemId == null ? null : problemId.toString();
    }

    // -----------------------------------------------------------------------
    // UUID accessors for internal repository / service use
    // -----------------------------------------------------------------------

    public UUID getIdAsUuid() {
        return id;
    }

    public UUID getUserIdAsUuid() {
        return userId;
    }

    public UUID getProblemIdAsUuid() {
        return problemId;
    }
}
