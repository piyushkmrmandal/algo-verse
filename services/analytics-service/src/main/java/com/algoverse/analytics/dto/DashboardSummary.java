package com.algoverse.analytics.dto;

import com.algoverse.analytics.domain.ProblemStat;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Platform-wide dashboard summary returned by GET /api/v1/analytics/dashboard.
 */
public record DashboardSummary(
        long totalUsers,
        long totalSubmissions,
        double acceptanceRate,
        long activeToday,
        long totalBadgesEarned,
        long totalXpAwarded,
        List<ProblemStatSummary> topProblems,
        Instant generatedAt
) implements Serializable {

    public record ProblemStatSummary(
            String problemSlug,
            String difficulty,
            long totalAttempts,
            long totalAccepted,
            double acceptanceRate
    ) implements Serializable {

        public static ProblemStatSummary from(ProblemStat stat) {
            double rate = stat.getTotalAttempts() == 0 ? 0.0
                    : (double) stat.getTotalAccepted() / stat.getTotalAttempts() * 100.0;
            return new ProblemStatSummary(
                    stat.getProblemSlug(),
                    stat.getDifficulty(),
                    stat.getTotalAttempts(),
                    stat.getTotalAccepted(),
                    Math.round(rate * 100.0) / 100.0
            );
        }
    }
}
