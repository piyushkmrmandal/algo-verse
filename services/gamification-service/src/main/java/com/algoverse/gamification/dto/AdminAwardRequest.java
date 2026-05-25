package com.algoverse.gamification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request body for POST /api/v1/gamification/admin/award
 */
public record AdminAwardRequest(
        @NotNull UUID userId,
        Integer xpAmount,
        String badgeSlug,
        String reason
) {
    public boolean hasXp() {
        return xpAmount != null && xpAmount > 0;
    }

    public boolean hasBadge() {
        return badgeSlug != null && !badgeSlug.isBlank();
    }
}
