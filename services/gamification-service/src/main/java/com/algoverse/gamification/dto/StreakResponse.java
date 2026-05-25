package com.algoverse.gamification.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for GET /api/v1/gamification/streak/{userId}
 */
public record StreakResponse(
        UUID userId,
        int currentStreak,
        int longestStreak,
        LocalDate lastActivityDate,
        int freezeCount
) {
    public static StreakResponse empty(UUID userId) {
        return new StreakResponse(userId, 0, 0, null, 2);
    }
}
