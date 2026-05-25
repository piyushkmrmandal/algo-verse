package com.algoverse.analytics.service;

import com.algoverse.analytics.domain.RawEvent;
import com.algoverse.analytics.dto.UserAnalytics;
import com.algoverse.analytics.repository.RawEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsService {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int XP_HISTORY_DAYS = 30;
    private static final int HEATMAP_DAYS = 365;

    private final RawEventRepository rawEventRepository;

    /**
     * Returns comprehensive analytics for a specific user.
     */
    public UserAnalytics getUserAnalytics(String userId) {
        log.debug("Computing analytics for userId={}", userId);

        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(XP_HISTORY_DAYS));
        Instant yearAgo = now.minus(Duration.ofDays(HEATMAP_DAYS));

        // Fetch accepted submissions (all time)
        List<RawEvent> acceptedSubmissions = rawEventRepository.findAcceptedSubmissionsByUserId(userId);
        long totalSolves = acceptedSubmissions.size();

        // Solves by difficulty
        Map<String, Long> solvesByDifficulty = acceptedSubmissions.stream()
                .collect(Collectors.groupingBy(
                        e -> extractString(e.getPayload(), "difficulty", "UNKNOWN").toUpperCase(),
                        Collectors.counting()
                ));

        // XP history — last 30 days
        List<RawEvent> xpEvents = rawEventRepository.findByUserIdAndEventTypeAndOccurredAtBetween(
                userId, "XP_AWARDED", thirtyDaysAgo, now);

        List<UserAnalytics.XpEvent> xpHistory = xpEvents.stream()
                .map(e -> new UserAnalytics.XpEvent(
                        toLocalDate(e.getOccurredAt()),
                        extractInt(e.getPayload(), "xpAmount", 0),
                        extractString(e.getPayload(), "reason", "")
                ))
                .sorted(Comparator.comparing(UserAnalytics.XpEvent::date))
                .toList();

        // Solve heatmap — last 365 days
        List<RawEvent> recentSolves = rawEventRepository
                .findAcceptedSubmissionsByUserIdAndDateRange(userId, yearAgo, now);

        Map<String, Long> solveHeatmap = recentSolves.stream()
                .collect(Collectors.groupingBy(
                        e -> toLocalDate(e.getOccurredAt()).format(DATE_KEY_FORMAT),
                        Collectors.counting()
                ));

        // Current streak (consecutive days with at least one solve ending today or yesterday)
        int currentStreak = computeCurrentStreak(acceptedSubmissions);

        return new UserAnalytics(
                userId,
                totalSolves,
                currentStreak,
                solvesByDifficulty,
                xpHistory,
                solveHeatmap
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int computeCurrentStreak(List<RawEvent> acceptedSubmissions) {
        if (acceptedSubmissions.isEmpty()) return 0;

        // Collect unique solve dates (sorted descending)
        TreeSet<LocalDate> solveDates = acceptedSubmissions.stream()
                .map(e -> toLocalDate(e.getOccurredAt()))
                .collect(Collectors.toCollection(TreeSet::new));

        LocalDate today = LocalDate.now();
        LocalDate checkDate = solveDates.contains(today) ? today : today.minusDays(1);

        if (!solveDates.contains(checkDate)) return 0;

        int streak = 0;
        while (solveDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private String extractString(Map<String, Object> payload, String key, String defaultValue) {
        if (payload == null) return defaultValue;
        Object value = payload.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int extractInt(Map<String, Object> payload, String key, int defaultValue) {
        if (payload == null) return defaultValue;
        Object value = payload.get(key);
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
