package com.algoverse.analytics.service;

import com.algoverse.analytics.domain.PlatformMetric;
import com.algoverse.analytics.domain.ProblemStat;
import com.algoverse.analytics.dto.GrowthDataPoint;
import com.algoverse.analytics.dto.PlatformGrowthResponse;
import com.algoverse.analytics.dto.ProblemStats;
import com.algoverse.analytics.repository.PlatformMetricRepository;
import com.algoverse.analytics.repository.ProblemStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformGrowthService {

    private static final Map<String, Integer> PERIOD_TO_DAYS = Map.of(
            "7d", 7,
            "30d", 30,
            "90d", 90
    );
    private static final int TOP_N = 5;

    private final PlatformMetricRepository platformMetricRepository;
    private final ProblemStatRepository problemStatRepository;

    /**
     * Returns a user growth and submission volume time series for the given period.
     * Cached with a 5-minute TTL (configured in RedisConfig).
     */
    @Cacheable(value = "analytics:growth", key = "#period")
    @Transactional(readOnly = true)
    public PlatformGrowthResponse getPlatformGrowth(String period) {
        int days = PERIOD_TO_DAYS.getOrDefault(period, 7);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        log.debug("Computing platform growth for period={} ({} to {})", period, startDate, endDate);

        List<PlatformMetric> signupMetrics = platformMetricRepository
                .findByMetricKeyAndMetricDateBetween("daily_signups", startDate, endDate);
        List<PlatformMetric> submissionMetrics = platformMetricRepository
                .findByMetricKeyAndMetricDateBetween("daily_submissions", startDate, endDate);

        // Build date-keyed maps
        Map<LocalDate, Long> signupsByDate = signupMetrics.stream()
                .collect(Collectors.toMap(PlatformMetric::getMetricDate, PlatformMetric::getMetricValue,
                        Long::sum));
        Map<LocalDate, Long> submissionsByDate = submissionMetrics.stream()
                .collect(Collectors.toMap(PlatformMetric::getMetricDate, PlatformMetric::getMetricValue,
                        Long::sum));

        // Fill every date in range, zero-filling missing days
        List<GrowthDataPoint> dataPoints = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            dataPoints.add(new GrowthDataPoint(
                    cursor,
                    signupsByDate.getOrDefault(cursor, 0L),
                    submissionsByDate.getOrDefault(cursor, 0L)
            ));
            cursor = cursor.plusDays(1);
        }

        long totalSignups = dataPoints.stream().mapToLong(GrowthDataPoint::signups).sum();
        long totalSubmissions = dataPoints.stream().mapToLong(GrowthDataPoint::submissions).sum();

        return new PlatformGrowthResponse(period, totalSignups, totalSubmissions, dataPoints);
    }

    /**
     * Returns per-problem stats: most attempted, hardest, and best acceptance rate.
     * Cached with a 2-minute TTL.
     */
    @Cacheable(value = "analytics:problems", key = "'stats'")
    @Transactional(readOnly = true)
    public ProblemStats getProblemStats() {
        log.debug("Computing problem stats from DB");
        Pageable top5 = PageRequest.of(0, TOP_N);

        List<ProblemStats.ProblemEntry> mostAttempted =
                problemStatRepository.findAllByOrderByTotalAttemptsDesc(top5)
                        .stream()
                        .map(this::toProblemEntry)
                        .toList();

        List<ProblemStats.ProblemEntry> hardest =
                problemStatRepository.findHardestProblems(top5)
                        .stream()
                        .map(this::toProblemEntry)
                        .toList();

        List<ProblemStats.ProblemEntry> bestAcceptance =
                problemStatRepository.findBestAcceptanceRateProblems(top5)
                        .stream()
                        .map(this::toProblemEntry)
                        .toList();

        return new ProblemStats(mostAttempted, hardest, bestAcceptance);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProblemStats.ProblemEntry toProblemEntry(ProblemStat ps) {
        double rate = ps.getTotalAttempts() == 0 ? 0.0
                : Math.round((double) ps.getTotalAccepted() / ps.getTotalAttempts() * 10000.0) / 100.0;
        return new ProblemStats.ProblemEntry(
                ps.getProblemSlug(),
                ps.getDifficulty(),
                ps.getTotalAttempts(),
                ps.getTotalAccepted(),
                rate
        );
    }
}
