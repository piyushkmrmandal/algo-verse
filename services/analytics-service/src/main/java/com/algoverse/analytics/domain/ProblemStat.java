package com.algoverse.analytics.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-problem aggregated statistics stored in PostgreSQL.
 * Tracks attempt counts and acceptance counts for each problem.
 */
@Entity
@Table(
        name = "problem_stats",
        indexes = {
                @Index(name = "idx_problem_stats_slug", columnList = "problem_slug"),
                @Index(name = "idx_problem_stats_difficulty", columnList = "difficulty")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ProblemStat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "problem_slug", nullable = false, unique = true, length = 255)
    private String problemSlug;

    @Column(name = "difficulty", nullable = false, length = 10)
    private String difficulty;

    @Column(name = "total_attempts", nullable = false)
    @Builder.Default
    private Long totalAttempts = 0L;

    @Column(name = "total_accepted", nullable = false)
    @Builder.Default
    private Long totalAccepted = 0L;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
