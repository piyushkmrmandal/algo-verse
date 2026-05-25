package com.algoverse.analytics.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Per-user analytics response for GET /api/v1/analytics/user/{userId}.
 */
public record UserAnalytics(
        String userId,
        long totalSolves,
        int currentStreak,
        Map<String, Long> solvesByDifficulty,
        List<XpEvent> xpHistory,
        Map<String, Long> solveHeatmap
) implements Serializable {

    /**
     * A single XP-awarded event in the user's history.
     */
    public record XpEvent(
            LocalDate date,
            int xpAmount,
            String reason
    ) implements Serializable {}
}
