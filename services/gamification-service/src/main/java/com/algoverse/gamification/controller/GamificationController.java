package com.algoverse.gamification.controller;

import com.algoverse.gamification.dto.*;
import com.algoverse.gamification.service.BadgeService;
import com.algoverse.gamification.service.LeaderboardService;
import com.algoverse.gamification.service.StreakService;
import com.algoverse.gamification.service.XpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing gamification endpoints.
 *
 * <p>Base path: {@code /api/v1/gamification}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gamification")
@RequiredArgsConstructor
@Tag(name = "Gamification", description = "XP, Streaks, Badges and Leaderboard APIs")
public class GamificationController {

    private final XpService xpService;
    private final StreakService streakService;
    private final BadgeService badgeService;
    private final LeaderboardService leaderboardService;

    // -----------------------------------------------------------------------
    // XP
    // -----------------------------------------------------------------------

    @GetMapping("/xp/{userId}")
    @Operation(summary = "Get user XP profile",
               description = "Returns current XP, level, XP to next level, and solve counts")
    public ResponseEntity<XpResponse> getXp(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        log.debug("GET /xp/{}", userId);
        return ResponseEntity.ok(xpService.getXp(userId));
    }

    // -----------------------------------------------------------------------
    // Streak
    // -----------------------------------------------------------------------

    @GetMapping("/streak/{userId}")
    @Operation(summary = "Get user streak",
               description = "Returns current streak days, longest streak, and last activity date")
    public ResponseEntity<StreakResponse> getStreak(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        log.debug("GET /streak/{}", userId);
        return ResponseEntity.ok(streakService.getStreak(userId));
    }

    // -----------------------------------------------------------------------
    // Badges
    // -----------------------------------------------------------------------

    @GetMapping("/badges/{userId}")
    @Operation(summary = "Get user badges",
               description = "Returns list of all badges earned by the user, most recent first")
    public ResponseEntity<List<BadgeResponse>> getBadges(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        log.debug("GET /badges/{}", userId);
        return ResponseEntity.ok(badgeService.getBadges(userId));
    }

    // -----------------------------------------------------------------------
    // Leaderboard
    // -----------------------------------------------------------------------

    @GetMapping("/leaderboard")
    @Operation(summary = "Get global leaderboard",
               description = "Returns paginated leaderboard sorted by total XP (Redis sorted set, DB fallback)")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        log.debug("GET /leaderboard?page={}&size={}", page, size);
        // Enforce sensible limits
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return ResponseEntity.ok(leaderboardService.getLeaderboard(safePage, safeSize));
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    @PostMapping("/admin/award")
    @Operation(summary = "Manually award XP or badge (admin only)",
               description = "Internal endpoint for admin overrides. Should be protected by an API gateway auth filter.")
    public ResponseEntity<String> adminAward(@Valid @RequestBody AdminAwardRequest request) {
        log.info("Admin award request: userId={} xp={} badge={}",
                request.userId(), request.xpAmount(), request.badgeSlug());

        StringBuilder result = new StringBuilder("Award applied:");

        if (request.hasXp()) {
            xpService.awardXp(request.userId(), request.xpAmount());
            result.append(" xp=").append(request.xpAmount());
        }

        if (request.hasBadge()) {
            badgeService.awardBadge(request.userId(), request.badgeSlug())
                    .ifPresentOrElse(
                            b -> result.append(" badge=").append(b.slug()),
                            () -> result.append(" badge=NOT_FOUND(").append(request.badgeSlug()).append(")")
                    );
        }

        if (!request.hasXp() && !request.hasBadge()) {
            return ResponseEntity.badRequest().body("Request must specify xpAmount or badgeSlug");
        }

        return ResponseEntity.ok(result.toString());
    }
}
