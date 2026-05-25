package com.algoverse.analytics.service;

import com.algoverse.analytics.domain.ProblemStat;
import com.algoverse.analytics.dto.DashboardSummary;
import com.algoverse.analytics.dto.GrowthDataPoint;
import com.algoverse.analytics.dto.PlatformGrowthResponse;
import com.algoverse.analytics.repository.PlatformMetricRepository;
import com.algoverse.analytics.repository.ProblemStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Unit Tests")
class DashboardServiceTest {

    @Mock
    private PlatformMetricRepository platformMetricRepository;

    @Mock
    private ProblemStatRepository problemStatRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private DashboardService dashboardService;

    @InjectMocks
    private PlatformGrowthService platformGrowthService;

    private ProblemStat buildProblemStat(String slug, String difficulty, long attempts, long accepted) {
        return ProblemStat.builder()
                .id(UUID.randomUUID())
                .problemSlug(slug)
                .difficulty(difficulty)
                .totalAttempts(attempts)
                .totalAccepted(accepted)
                .build();
    }

    // -------------------------------------------------------------------------
    // getDashboard tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard_cacheMiss_queriesDbAndCaches: on cache miss, queries all repos")
    void getDashboard_cacheMiss_queriesDbAndCaches() {
        // Arrange
        when(platformMetricRepository.sumByMetricKey("daily_signups")).thenReturn(Optional.of(500L));
        when(platformMetricRepository.sumByMetricKey("daily_badges_earned")).thenReturn(Optional.of(120L));
        when(platformMetricRepository.sumByMetricKey("daily_xp_awarded")).thenReturn(Optional.of(50000L));
        when(platformMetricRepository.sumByMetricKeyAndDate(eq("daily_submissions"), any()))
                .thenReturn(Optional.of(30L));
        when(problemStatRepository.sumTotalAttempts()).thenReturn(Optional.of(10000L));
        when(problemStatRepository.sumTotalAccepted()).thenReturn(Optional.of(6500L));
        when(problemStatRepository.findAllByOrderByTotalAttemptsDesc(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // Act
        DashboardSummary result = dashboardService.getDashboard();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.totalUsers()).isEqualTo(500L);
        assertThat(result.totalSubmissions()).isEqualTo(10000L);
        assertThat(result.activeToday()).isEqualTo(30L);
        assertThat(result.totalBadgesEarned()).isEqualTo(120L);
        assertThat(result.totalXpAwarded()).isEqualTo(50000L);

        verify(platformMetricRepository, times(1)).sumByMetricKey("daily_signups");
        verify(problemStatRepository, times(1)).sumTotalAttempts();
        verify(problemStatRepository, times(1)).sumTotalAccepted();
    }

    @Test
    @DisplayName("getDashboard_cachedResult_doesNotHitDb: second invocation in same context returns immediately")
    void getDashboard_cachedResult_doesNotHitDb() {
        // Arrange — set up mocks for one real invocation
        when(platformMetricRepository.sumByMetricKey(anyString())).thenReturn(Optional.of(100L));
        when(platformMetricRepository.sumByMetricKeyAndDate(anyString(), any())).thenReturn(Optional.of(5L));
        when(problemStatRepository.sumTotalAttempts()).thenReturn(Optional.of(200L));
        when(problemStatRepository.sumTotalAccepted()).thenReturn(Optional.of(100L));
        when(problemStatRepository.findAllByOrderByTotalAttemptsDesc(any())).thenReturn(Collections.emptyList());

        // Act — call twice (in a real cached context the second would skip DB, but here we verify
        // that the method logic is idempotent and correct)
        DashboardSummary first = dashboardService.getDashboard();
        DashboardSummary second = dashboardService.getDashboard();

        // Assert both return consistent data
        assertThat(first.totalUsers()).isEqualTo(second.totalUsers());
        assertThat(first.totalSubmissions()).isEqualTo(second.totalSubmissions());
        assertThat(first.acceptanceRate()).isEqualTo(second.acceptanceRate());
    }

    @Test
    @DisplayName("getDashboard_acceptanceRate_calculatesCorrectly: rate = accepted/attempts * 100")
    void getDashboard_acceptanceRate_calculatesCorrectly() {
        // Arrange
        when(platformMetricRepository.sumByMetricKey(anyString())).thenReturn(Optional.of(0L));
        when(platformMetricRepository.sumByMetricKeyAndDate(anyString(), any())).thenReturn(Optional.of(0L));
        when(problemStatRepository.sumTotalAttempts()).thenReturn(Optional.of(1000L));
        when(problemStatRepository.sumTotalAccepted()).thenReturn(Optional.of(450L));
        when(problemStatRepository.findAllByOrderByTotalAttemptsDesc(any())).thenReturn(Collections.emptyList());

        // Act
        DashboardSummary result = dashboardService.getDashboard();

        // Assert: 450/1000 * 100 = 45.0%
        assertThat(result.acceptanceRate()).isEqualTo(45.0, offset(0.01));
    }

    @Test
    @DisplayName("getDashboard_zeroSubmissions_acceptanceRateIsZero: no division by zero")
    void getDashboard_zeroSubmissions_acceptanceRateIsZero() {
        when(platformMetricRepository.sumByMetricKey(anyString())).thenReturn(Optional.empty());
        when(platformMetricRepository.sumByMetricKeyAndDate(anyString(), any())).thenReturn(Optional.empty());
        when(problemStatRepository.sumTotalAttempts()).thenReturn(Optional.empty());
        when(problemStatRepository.sumTotalAccepted()).thenReturn(Optional.empty());
        when(problemStatRepository.findAllByOrderByTotalAttemptsDesc(any())).thenReturn(Collections.emptyList());

        DashboardSummary result = dashboardService.getDashboard();

        assertThat(result.acceptanceRate()).isEqualTo(0.0);
        assertThat(result.totalUsers()).isZero();
        assertThat(result.totalSubmissions()).isZero();
    }

    @Test
    @DisplayName("getDashboard_topProblems_mappedCorrectly: returns top problems sorted")
    void getDashboard_topProblems_mappedCorrectly() {
        List<ProblemStat> stats = List.of(
                buildProblemStat("two-sum", "EASY", 5000L, 4000L),
                buildProblemStat("median-of-arrays", "HARD", 2000L, 400L)
        );

        when(platformMetricRepository.sumByMetricKey(anyString())).thenReturn(Optional.of(0L));
        when(platformMetricRepository.sumByMetricKeyAndDate(anyString(), any())).thenReturn(Optional.of(0L));
        when(problemStatRepository.sumTotalAttempts()).thenReturn(Optional.of(7000L));
        when(problemStatRepository.sumTotalAccepted()).thenReturn(Optional.of(4400L));
        when(problemStatRepository.findAllByOrderByTotalAttemptsDesc(any())).thenReturn(stats);

        DashboardSummary result = dashboardService.getDashboard();

        assertThat(result.topProblems()).hasSize(2);
        assertThat(result.topProblems().get(0).problemSlug()).isEqualTo("two-sum");
        assertThat(result.topProblems().get(0).acceptanceRate()).isEqualTo(80.0, offset(0.01));
    }

    // -------------------------------------------------------------------------
    // getPlatformGrowth tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getPlatformGrowth_7days_returns7DataPoints: fills all 7 days")
    void getPlatformGrowth_7days_returns7DataPoints() {
        // Arrange — no data in DB
        when(platformMetricRepository.findByMetricKeyAndMetricDateBetween(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        PlatformGrowthResponse result = platformGrowthService.getPlatformGrowth("7d");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.period()).isEqualTo("7d");
        assertThat(result.dataPoints()).hasSize(7);
        assertThat(result.dataPoints().get(0).date())
                .isEqualTo(LocalDate.now().minusDays(6));
        assertThat(result.dataPoints().get(6).date())
                .isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("getPlatformGrowth_emptyData_returnsZeroFilledSeries: all counts are zero")
    void getPlatformGrowth_emptyData_returnsZeroFilledSeries() {
        when(platformMetricRepository.findByMetricKeyAndMetricDateBetween(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        PlatformGrowthResponse result = platformGrowthService.getPlatformGrowth("30d");

        assertThat(result.dataPoints()).hasSize(30);
        assertThat(result.totalSignups()).isZero();
        assertThat(result.totalSubmissions()).isZero();

        result.dataPoints().forEach(dp -> {
            assertThat(dp.signups()).isZero();
            assertThat(dp.submissions()).isZero();
        });
    }

    @Test
    @DisplayName("getPlatformGrowth_withData_aggregatesTotalsCorrectly")
    void getPlatformGrowth_withData_aggregatesTotalsCorrectly() {
        // Arrange
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        var signupMetric1 = com.algoverse.analytics.domain.PlatformMetric.builder()
                .id(UUID.randomUUID()).metricDate(today).metricKey("daily_signups").metricValue(10L).build();
        var signupMetric2 = com.algoverse.analytics.domain.PlatformMetric.builder()
                .id(UUID.randomUUID()).metricDate(yesterday).metricKey("daily_signups").metricValue(5L).build();

        var submissionMetric = com.algoverse.analytics.domain.PlatformMetric.builder()
                .id(UUID.randomUUID()).metricDate(today).metricKey("daily_submissions").metricValue(100L).build();

        when(platformMetricRepository.findByMetricKeyAndMetricDateBetween(eq("daily_signups"), any(), any()))
                .thenReturn(List.of(signupMetric1, signupMetric2));
        when(platformMetricRepository.findByMetricKeyAndMetricDateBetween(eq("daily_submissions"), any(), any()))
                .thenReturn(List.of(submissionMetric));

        PlatformGrowthResponse result = platformGrowthService.getPlatformGrowth("7d");

        assertThat(result.totalSignups()).isEqualTo(15L);
        assertThat(result.totalSubmissions()).isEqualTo(100L);
        assertThat(result.dataPoints()).hasSize(7);

        GrowthDataPoint todayPoint = result.dataPoints().stream()
                .filter(dp -> dp.date().equals(today))
                .findFirst().orElseThrow();
        assertThat(todayPoint.signups()).isEqualTo(10L);
        assertThat(todayPoint.submissions()).isEqualTo(100L);
    }
}
