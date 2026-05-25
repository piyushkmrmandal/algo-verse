package com.algoverse.analytics.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Problem-level stats response for GET /api/v1/analytics/problems/stats.
 */
public record ProblemStats(
        List<ProblemEntry> mostAttempted,
        List<ProblemEntry> hardest,
        List<ProblemEntry> bestAcceptanceRate
) implements Serializable {

    public record ProblemEntry(
            String problemSlug,
            String difficulty,
            long totalAttempts,
            long totalAccepted,
            double acceptanceRate
    ) implements Serializable {}
}
