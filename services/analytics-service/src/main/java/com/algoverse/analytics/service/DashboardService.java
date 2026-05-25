package com.algoverse.analytics.service;

import com.algoverse.analytics.domain.ProblemStat;
import com.algoverse.analytics.dto.DashboardSummary;
import com.algoverse.analytics.repository.PlatformMetricRepository;
import com.algoverse.analytics.repository.ProblemStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final String CACHE_KEY = "analytics:dashboard";
    private static final int TOP_PROBLEMS_LIMIT = 5;

    private final PlatformMetricRepository platformMetricRepository;
    private final ProblemStatRepository problemStatRepository;

    /**
     * Returns the platform-wide dashboard summary.
     * Result is cached in Redis with a 60-second TTL (configured in RedisConfig).
     */
    @Cacheable(value = CACHE_KEY, key = "'summary'")
    @Transactional(readOnly = true)
    public DashboardSummary getDashboard() {
        log.debug("Cache miss — computing dashboard summary from DB");

        // Total users = sum of all daily_signups ever
        long totalUsers = platformMetricRepository.sumByMetricKey("daily_signups").orElse(0L);

        // Total submissions & acceptance rate from problem_stats
        long totalSubmissions = problemStatRepository.sumTotalAttempts().orElse(0L);
        long totalAccepted = problemStatRepository.sumTotalAccepted().orElse(0L);
        double acceptanceRate = totalSubmissions == 0 ? 0.0
                : Math.round((double) totalAccepted / totalSubmissions * 10000.0) / 100.0;

        // Active today = daily_submissions for today
        long activeToday = platformMetricRepository
                .sumByMetricKeyAndDate("daily_submissions", LocalDate.now())
                .orElse(0L);

        // Total badges earned
        long totalBadgesEarned = platformMetricRepository.sumByMetricKey("daily_badges_earned").orElse(0L);

        // Total XP awarded
        long totalXpAwarded = platformMetricRepository.sumByMetricKey("daily_xp_awarded").orElse(0L);

        // Top 5 problems by total_attempts
        List<ProblemStat> topProblemStats = problemStatRepository
                .findAllByOrderByTotalAttemptsDesc(PageRequest.of(0, TOP_PROBLEMS_LIMIT));

        List<DashboardSummary.ProblemStatSummary> topProblems = topProblemStats.stream()
                .map(DashboardSummary.ProblemStatSummary::from)
                .toList();

        return new DashboardSummary(
                totalUsers,
                totalSubmissions,
                acceptanceRate,
                activeToday,
                totalBadgesEarned,
                totalXpAwarded,
                topProblems,
                Instant.now()
        );
    }

    /**
     * Evict the dashboard cache every 60 seconds to ensure freshness.
     */
    @Scheduled(fixedDelay = 60_000)
    @CacheEvict(value = CACHE_KEY, key = "'summary'")
    public void evictDashboardCache() {
        log.debug("Dashboard cache evicted by scheduler");
    }
}
