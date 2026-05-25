package com.algoverse.gamification.dto;

import java.util.UUID;

/**
 * A single entry in the leaderboard response.
 */
public record LeaderboardEntry(
        long rank,
        UUID userId,
        String displayName,
        int totalXp,
        int level
) {}
