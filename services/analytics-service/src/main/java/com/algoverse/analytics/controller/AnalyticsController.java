package com.algoverse.analytics.controller;

import com.algoverse.analytics.dto.*;
import com.algoverse.analytics.service.DashboardService;
import com.algoverse.analytics.service.PlatformGrowthService;
import com.algoverse.analytics.service.UserAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
@Tag(name = "Analytics", description = "Platform analytics, user stats and growth metrics")
public class AnalyticsController {

    private final DashboardService dashboardService;
    private final UserAnalyticsService userAnalyticsService;
    private final PlatformGrowthService platformGrowthService;

    /**
     * GET /api/v1/analytics/dashboard
     * Platform-wide summary: total users, total submissions, acceptance rate, active today.
     */
    @GetMapping("/dashboard")
    @Operation(
            summary = "Platform dashboard summary",
            description = "Returns cached platform-wide metrics. TTL = 60 seconds."
    )
    public ResponseEntity<DashboardSummary> getDashboard() {
        log.info("GET /api/v1/analytics/dashboard");
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    /**
     * GET /api/v1/analytics/user/{userId}
     * Per-user analytics: solves by difficulty, XP history, heatmap, streak.
     */
    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Per-user analytics",
            description = "Returns solve stats, XP history (last 30d), and solve heatmap (last 365d) for a user."
    )
    public ResponseEntity<UserAnalytics> getUserAnalytics(
            @Parameter(description = "Target user ID", required = true)
            @PathVariable String userId
    ) {
        log.info("GET /api/v1/analytics/user/{}", userId);
        return ResponseEntity.ok(userAnalyticsService.getUserAnalytics(userId));
    }

    /**
     * GET /api/v1/analytics/problems/stats
     * Problem-level stats: most attempted, hardest, best acceptance rate.
     */
    @GetMapping("/problems/stats")
    @Operation(
            summary = "Problem-level stats",
            description = "Returns top-5 most attempted, hardest, and best acceptance rate problems."
    )
    public ResponseEntity<ProblemStats> getProblemStats() {
        log.info("GET /api/v1/analytics/problems/stats");
        return ResponseEntity.ok(platformGrowthService.getProblemStats());
    }

    /**
     * GET /api/v1/analytics/leaderboard/trends
     * XP gain trends over last 7 / 30 days.
     */
    @GetMapping("/leaderboard/trends")
    @Operation(
            summary = "Leaderboard XP trends",
            description = "Returns XP gain trends over the last 7 or 30 days."
    )
    public ResponseEntity<PlatformGrowthResponse> getLeaderboardTrends(
            @Parameter(description = "Period: 7d or 30d", example = "7d")
            @RequestParam(defaultValue = "7d") String period
    ) {
        log.info("GET /api/v1/analytics/leaderboard/trends?period={}", period);
        // Reuse growth data — XP trends follow submission volume over the same windows
        String normalizedPeriod = period.matches("7d|30d|90d") ? period : "7d";
        return ResponseEntity.ok(platformGrowthService.getPlatformGrowth(normalizedPeriod));
    }

    /**
     * GET /api/v1/analytics/platform/growth?period=7d|30d|90d
     * User growth and submission volume time series.
     */
    @GetMapping("/platform/growth")
    @Operation(
            summary = "Platform growth time series",
            description = "Returns daily signups and submission volume for 7d, 30d, or 90d."
    )
    public ResponseEntity<PlatformGrowthResponse> getPlatformGrowth(
            @Parameter(description = "Period: 7d, 30d, or 90d", example = "30d")
            @RequestParam(defaultValue = "7d") String period
    ) {
        log.info("GET /api/v1/analytics/platform/growth?period={}", period);
        String normalizedPeriod = period.matches("7d|30d|90d") ? period : "7d";
        return ResponseEntity.ok(platformGrowthService.getPlatformGrowth(normalizedPeriod));
    }
}
