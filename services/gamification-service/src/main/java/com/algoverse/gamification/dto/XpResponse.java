package com.algoverse.gamification.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for GET /api/v1/gamification/xp/{userId}
 */
public record XpResponse(
        UUID userId,
        int totalXp,
        int level,
        int xpToNextLevel,
        int weeklyXp,
        int monthlyXp,
        int easySolves,
        int mediumSolves,
        int hardSolves,
        int totalSolves,
        Instant updatedAt
) {
    public static XpResponse empty(UUID userId) {
        return new XpResponse(userId, 0, 1, 100, 0, 0, 0, 0, 0, 0, Instant.now());
    }
}
