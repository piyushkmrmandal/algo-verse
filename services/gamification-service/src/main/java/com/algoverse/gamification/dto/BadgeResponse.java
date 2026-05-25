package com.algoverse.gamification.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for a single earned badge in GET /api/v1/gamification/badges/{userId}
 */
public record BadgeResponse(
        UUID badgeId,
        String slug,
        String name,
        String description,
        String iconUrl,
        String rarity,
        int xpReward,
        Instant earnedAt
) {}
