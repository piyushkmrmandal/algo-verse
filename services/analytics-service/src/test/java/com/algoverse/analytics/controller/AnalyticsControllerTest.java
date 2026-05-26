package com.algoverse.analytics.controller;

import com.algoverse.analytics.dto.*;
import com.algoverse.analytics.service.DashboardService;
import com.algoverse.analytics.service.PlatformGrowthService;
import com.algoverse.analytics.service.UserAnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AnalyticsController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("AnalyticsController WebMvc slice tests")
class AnalyticsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DashboardService dashboardService;
    @MockBean UserAnalyticsService userAnalyticsService;
    @MockBean PlatformGrowthService platformGrowthService;

    // ── GET /dashboard ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/analytics/dashboard returns 200 with dashboard payload")
    void getDashboard_returns200WithPayload() throws Exception {
        DashboardSummary summary = new DashboardSummary(
                1500L, 20000L, 65.0, 120L, 450L, 900000L,
                Collections.emptyList(), Instant.now()
        );
        when(dashboardService.getDashboard()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(1500)))
                .andExpect(jsonPath("$.totalSubmissions", is(20000)))
                .andExpect(jsonPath("$.acceptanceRate", is(65.0)))
                .andExpect(jsonPath("$.activeToday", is(120)))
                .andExpect(jsonPath("$.topProblems", hasSize(0)));

        verify(dashboardService).getDashboard();
    }

    @Test
    @DisplayName("GET /api/v1/analytics/dashboard includes top problems in response")
    void getDashboard_includesTopProblems() throws Exception {
        List<DashboardSummary.ProblemStatSummary> topProblems = List.of(
                new DashboardSummary.ProblemStatSummary("two-sum", "EASY", 5000L, 4000L, 80.0),
                new DashboardSummary.ProblemStatSummary("binary-search", "EASY", 3000L, 2700L, 90.0)
        );
        DashboardSummary summary = new DashboardSummary(
                1000L, 8000L, 85.0, 50L, 200L, 400000L,
                topProblems, Instant.now()
        );
        when(dashboardService.getDashboard()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProblems", hasSize(2)))
                .andExpect(jsonPath("$.topProblems[0].problemSlug", is("two-sum")))
                .andExpect(jsonPath("$.topProblems[1].problemSlug", is("binary-search")));
    }

    // ── GET /user/{userId} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/analytics/user/{userId} returns 200 with user analytics")
    void getUserAnalytics_returns200WithData() throws Exception {
        UserAnalytics analytics = new UserAnalytics(
                "user-abc", 42L, 7,
                Map.of("EASY", 20L, "MEDIUM", 15L, "HARD", 7L),
                Collections.emptyList(),
                Collections.emptyMap()
        );
        when(userAnalyticsService.getUserAnalytics("user-abc")).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/analytics/user/user-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("user-abc")))
                .andExpect(jsonPath("$.totalSolves", is(42)))
                .andExpect(jsonPath("$.currentStreak", is(7)))
                .andExpect(jsonPath("$.solvesByDifficulty.EASY", is(20)))
                .andExpect(jsonPath("$.solvesByDifficulty.HARD", is(7)));
    }

    // ── GET /problems/stats ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/analytics/problems/stats returns 200 with problem stats")
    void getProblemStats_returns200() throws Exception {
        List<ProblemStats.ProblemEntry> entries = List.of(
                new ProblemStats.ProblemEntry("two-sum", "EASY", 5000L, 4000L, 80.0)
        );
        ProblemStats stats = new ProblemStats(entries, Collections.emptyList(), entries);
        when(platformGrowthService.getProblemStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/problems/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostAttempted[0].problemSlug", is("two-sum")))
                .andExpect(jsonPath("$.mostAttempted[0].acceptanceRate", is(80.0)));
    }

    // ── GET /platform/growth ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/analytics/platform/growth?period=7d returns 200 with data points")
    void getPlatformGrowth_7d_returns200WithDataPoints() throws Exception {
        List<GrowthDataPoint> dataPoints = List.of(
                new GrowthDataPoint(LocalDate.now().minusDays(1), 5L, 100L),
                new GrowthDataPoint(LocalDate.now(), 3L, 80L)
        );
        PlatformGrowthResponse response = new PlatformGrowthResponse("7d", 8L, 180L, dataPoints);
        when(platformGrowthService.getPlatformGrowth("7d")).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/platform/growth").param("period", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period", is("7d")))
                .andExpect(jsonPath("$.totalSignups", is(8)))
                .andExpect(jsonPath("$.totalSubmissions", is(180)))
                .andExpect(jsonPath("$.dataPoints", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/platform/growth with unknown period normalizes to 7d")
    void getPlatformGrowth_unknownPeriod_normalizesTo7d() throws Exception {
        PlatformGrowthResponse response = new PlatformGrowthResponse(
                "7d", 0L, 0L, Collections.emptyList());
        when(platformGrowthService.getPlatformGrowth("7d")).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/platform/growth").param("period", "invalid"))
                .andExpect(status().isOk());

        verify(platformGrowthService).getPlatformGrowth("7d");
    }

    // ── GET /leaderboard/trends ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/analytics/leaderboard/trends defaults to 7d period")
    void getLeaderboardTrends_defaultPeriod_uses7d() throws Exception {
        PlatformGrowthResponse response = new PlatformGrowthResponse(
                "7d", 0L, 0L, Collections.emptyList());
        when(platformGrowthService.getPlatformGrowth("7d")).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/leaderboard/trends"))
                .andExpect(status().isOk());

        verify(platformGrowthService).getPlatformGrowth("7d");
    }
}
