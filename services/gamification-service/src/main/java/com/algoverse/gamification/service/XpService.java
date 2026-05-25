package com.algoverse.gamification.service;

import com.algoverse.gamification.domain.UserXp;
import com.algoverse.gamification.dto.XpResponse;
import com.algoverse.gamification.repository.UserXpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core XP award logic for the gamification pipeline.
 *
 * <h3>XP Rules</h3>
 * <table>
 *   <tr><th>Event</th><th>XP</th></tr>
 *   <tr><td>Solve EASY (first time)</td><td>+50</td></tr>
 *   <tr><td>Solve MEDIUM (first time)</td><td>+100</td></tr>
 *   <tr><td>Solve HARD (first time)</td><td>+200</td></tr>
 *   <tr><td>Daily streak bonus</td><td>+25</td></tr>
 *   <tr><td>7-day streak milestone</td><td>+150</td></tr>
 *   <tr><td>30-day streak milestone</td><td>+500</td></tr>
 * </table>
 *
 * <h3>Level Formula</h3>
 * {@code level = floor(sqrt(totalXp / 100)) + 1}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XpService {

    // XP awards per difficulty (first-solve only)
    public static final int XP_EASY_FIRST_SOLVE   = 50;
    public static final int XP_MEDIUM_FIRST_SOLVE = 100;
    public static final int XP_HARD_FIRST_SOLVE   = 200;

    // Streak bonuses
    public static final int XP_DAILY_STREAK_BONUS = 25;
    public static final int XP_7_DAY_MILESTONE    = 150;
    public static final int XP_30_DAY_MILESTONE   = 500;

    private final UserXpRepository userXpRepository;
    private final LeaderboardService leaderboardService;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the XP profile for a user, creating a default record if none exists.
     */
    @Transactional(readOnly = true)
    public XpResponse getXp(UUID userId) {
        return userXpRepository.findById(userId)
                .map(this::toResponse)
                .orElse(XpResponse.empty(userId));
    }

    /**
     * Awards XP for a first-solve event based on problem difficulty.
     * Also increments solve counters and updates the leaderboard.
     *
     * @param userId      target user
     * @param difficulty  problem difficulty (EASY, MEDIUM, HARD)
     * @param isFirstSolve whether this is the user's first accepted solve for the problem
     * @return updated XP profile
     */
    @Transactional
    public XpResponse awardForSolve(UUID userId, String difficulty, boolean isFirstSolve) {
        UserXp userXp = getOrCreate(userId);

        if (isFirstSolve) {
            int xpAmount = xpForDifficulty(difficulty);
            log.info("Awarding {} XP to userId={} for first {} solve", xpAmount, userId, difficulty);
            userXp.addXp(xpAmount);
        }

        userXp.incrementSolves(difficulty);
        UserXp saved = userXpRepository.save(userXp);
        leaderboardService.updateScore(userId, saved.getTotalXp(), resolveDisplayName(saved));

        return toResponse(saved);
    }

    /**
     * Awards a streak bonus. Called by {@link StreakService} after updating the streak.
     *
     * @param userId       target user
     * @param streakDays   the current streak day count (used to detect milestones)
     * @return updated XP profile
     */
    @Transactional
    public XpResponse awardStreakBonus(UUID userId, int streakDays) {
        UserXp userXp = getOrCreate(userId);

        int bonus = XP_DAILY_STREAK_BONUS;
        if (streakDays == 7) {
            bonus += XP_7_DAY_MILESTONE;
            log.info("7-day streak milestone bonus awarded to userId={}", userId);
        } else if (streakDays == 30) {
            bonus += XP_30_DAY_MILESTONE;
            log.info("30-day streak milestone bonus awarded to userId={}", userId);
        }

        log.info("Awarding {} streak XP to userId={} (streak={} days)", bonus, userId, streakDays);
        userXp.addXp(bonus);
        UserXp saved = userXpRepository.save(userXp);
        leaderboardService.updateScore(userId, saved.getTotalXp(), resolveDisplayName(saved));

        return toResponse(saved);
    }

    /**
     * Directly awards a specified XP amount (admin / badge reward).
     */
    @Transactional
    public XpResponse awardXp(UUID userId, int amount) {
        UserXp userXp = getOrCreate(userId);
        userXp.addXp(amount);
        UserXp saved = userXpRepository.save(userXp);
        leaderboardService.updateScore(userId, saved.getTotalXp(), resolveDisplayName(saved));
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Package-private helpers (used by StreakService, BadgeService)
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserXp getOrCreate(UUID userId) {
        return userXpRepository.findById(userId)
                .orElseGet(() -> {
                    log.info("Creating new UserXp record for userId={}", userId);
                    return new UserXp(userId);
                });
    }

    // -----------------------------------------------------------------------
    // Static helpers (used in tests and StreakService)
    // -----------------------------------------------------------------------

    /**
     * Returns first-solve XP for the given difficulty.
     */
    public static int xpForDifficulty(String difficulty) {
        return switch (difficulty.toUpperCase()) {
            case "EASY"   -> XP_EASY_FIRST_SOLVE;
            case "MEDIUM" -> XP_MEDIUM_FIRST_SOLVE;
            case "HARD"   -> XP_HARD_FIRST_SOLVE;
            default       -> XP_EASY_FIRST_SOLVE;
        };
    }

    /**
     * Computes the level from total XP.
     * Formula: level = floor(sqrt(totalXp / 100)) + 1
     */
    public static int computeLevel(int totalXp) {
        return UserXp.computeLevel(totalXp);
    }

    /**
     * Computes XP required to reach the next level.
     */
    public static int xpToNextLevel(int totalXp) {
        return UserXp.xpToNextLevel(totalXp);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private XpResponse toResponse(UserXp entity) {
        return new XpResponse(
                entity.getUserId(),
                entity.getTotalXp(),
                entity.getLevel(),
                UserXp.xpToNextLevel(entity.getTotalXp()),
                entity.getWeeklyXp(),
                entity.getMonthlyXp(),
                entity.getEasySolves(),
                entity.getMediumSolves(),
                entity.getHardSolves(),
                entity.getTotalSolves(),
                entity.getUpdatedAt()
        );
    }

    private String resolveDisplayName(UserXp userXp) {
        String name = userXp.getDisplayName();
        return (name != null && !name.isBlank()) ? name : userXp.getUserId().toString();
    }
}
