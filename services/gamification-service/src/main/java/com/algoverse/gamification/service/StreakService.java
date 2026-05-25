package com.algoverse.gamification.service;

import com.algoverse.gamification.domain.UserStreak;
import com.algoverse.gamification.dto.StreakResponse;
import com.algoverse.gamification.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Manages daily coding streaks.
 *
 * <h3>Streak Rules</h3>
 * <ul>
 *   <li>A streak increments when the user solves at least 1 problem per calendar day (UTC).</li>
 *   <li>If a user misses a day, the streak resets to 1 (the current day counts).</li>
 *   <li>Multiple solves on the same day do not increment the streak further.</li>
 *   <li>Streak bonuses are awarded by {@link XpService#awardStreakBonus}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreakService {

    private final UserStreakRepository userStreakRepository;
    private final XpService xpService;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the streak profile for a user.
     */
    @Transactional(readOnly = true)
    public StreakResponse getStreak(UUID userId) {
        return userStreakRepository.findById(userId)
                .map(this::toResponse)
                .orElse(StreakResponse.empty(userId));
    }

    /**
     * Records a problem solve for today (UTC). Handles three cases:
     * <ol>
     *   <li><b>Same day</b> — no-op, streak already counted.</li>
     *   <li><b>Consecutive day</b> — increment streak, award streak bonus XP.</li>
     *   <li><b>Missed day(s)</b> — reset streak to 1, award daily bonus XP.</li>
     * </ol>
     *
     * @param userId  the user who solved a problem
     * @return updated streak state
     */
    @Transactional
    public StreakResponse recordSolveForToday(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UserStreak streak = userStreakRepository.findById(userId)
                .orElseGet(() -> {
                    log.info("Creating new UserStreak record for userId={}", userId);
                    return new UserStreak(userId);
                });

        LocalDate lastActivity = streak.getLastActivityDate();

        if (today.equals(lastActivity)) {
            // Already counted today — idempotent no-op
            log.debug("Streak already updated today for userId={}", userId);
            return toResponse(streak);
        }

        boolean isConsecutive = lastActivity != null && today.equals(lastActivity.plusDays(1));
        int previousStreak = streak.getCurrentStreak();

        if (isConsecutive) {
            streak.setCurrentStreak(previousStreak + 1);
            log.info("Streak extended to {} days for userId={}", streak.getCurrentStreak(), userId);
        } else {
            // Missed one or more days — reset streak (current day counts as day 1)
            if (lastActivity != null) {
                log.info("Streak reset for userId={} (last activity: {}, today: {})",
                        userId, lastActivity, today);
            }
            streak.setCurrentStreak(1);
        }

        streak.setLastActivityDate(today);

        // Update longest streak
        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }

        UserStreak saved = userStreakRepository.save(streak);

        // Award streak-related XP only on increment (not on same-day duplicate)
        xpService.awardStreakBonus(userId, saved.getCurrentStreak());

        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private StreakResponse toResponse(UserStreak entity) {
        return new StreakResponse(
                entity.getUserId(),
                entity.getCurrentStreak(),
                entity.getLongestStreak(),
                entity.getLastActivityDate(),
                entity.getFreezeCount()
        );
    }
}
