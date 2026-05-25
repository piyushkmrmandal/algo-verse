package com.algoverse.gamification.service;

import com.algoverse.gamification.domain.Badge;
import com.algoverse.gamification.domain.UserBadge;
import com.algoverse.gamification.domain.UserXp;
import com.algoverse.gamification.dto.BadgeResponse;
import com.algoverse.gamification.repository.BadgeRepository;
import com.algoverse.gamification.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates and awards badges after each accepted submission.
 *
 * <h3>Badge Conditions</h3>
 * <ul>
 *   <li>{@code FIRST_SOLVE} — awarded on the user's very first accepted solve.</li>
 *   <li>{@code PROBLEMS_SOLVED} — threshold on per-difficulty or total solve count.</li>
 *   <li>{@code STREAK_DAYS} — awarded when streak reaches a threshold.</li>
 *   <li>{@code TOTAL_SOLVES} — awarded when total solves reach a threshold.</li>
 *   <li>{@code SPEED_SOLVE} — awarded when executionTimeMs &lt; maxExecutionTimeMs.</li>
 * </ul>
 *
 * <p>All award operations are idempotent: the UNIQUE constraint on (user_id, badge_id)
 * in {@code user_badges} prevents double-awarding at the DB level, and
 * {@link UserBadgeRepository#existsByUserIdAndBadgeId} provides a fast pre-check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final XpService xpService;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns all badges earned by the given user.
     */
    @Transactional(readOnly = true)
    public List<BadgeResponse> getBadges(UUID userId) {
        return userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId)
                .stream()
                .map(ub -> toResponse(ub.getBadge(), ub))
                .toList();
    }

    /**
     * Evaluates all badge conditions for the given user after an accepted submission.
     * Awards any newly earned badges and triggers XP rewards for each.
     *
     * @param userId          the user who submitted
     * @param difficulty      problem difficulty (EASY / MEDIUM / HARD)
     * @param isFirstSolve    whether this is the first accepted solve for this problem
     * @param streakDays      current streak after this solve
     * @param executionTimeMs how long the submission took to execute
     * @return list of newly awarded badges (empty if none)
     */
    @Transactional
    public List<BadgeResponse> evaluateAndAward(
            UUID userId,
            String difficulty,
            boolean isFirstSolve,
            int streakDays,
            long executionTimeMs) {

        UserXp userXp = xpService.getOrCreate(userId);
        List<Badge> allBadges = badgeRepository.findAll();
        List<BadgeResponse> awarded = new ArrayList<>();

        for (Badge badge : allBadges) {
            if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                continue; // already earned
            }

            boolean earned = evaluateCondition(badge, userXp, difficulty, isFirstSolve, streakDays, executionTimeMs);
            if (earned) {
                UserBadge userBadge = new UserBadge(userId, badge);
                userBadgeRepository.save(userBadge);

                // Award badge XP reward if any
                if (badge.getXpReward() > 0) {
                    xpService.awardXp(userId, badge.getXpReward());
                    log.info("Badge XP reward awarded: userId={} badge={} xpReward={}",
                            userId, badge.getSlug(), badge.getXpReward());
                }

                awarded.add(toResponse(badge, userBadge));
                log.info("Badge earned: userId={} badge={} rarity={}", userId, badge.getSlug(), badge.getRarity());
            }
        }

        return awarded;
    }

    /**
     * Manually awards a specific badge by slug (admin endpoint).
     */
    @Transactional
    public Optional<BadgeResponse> awardBadge(UUID userId, String badgeSlug) {
        return badgeRepository.findBySlug(badgeSlug).map(badge -> {
            if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                log.info("Badge {} already awarded to userId={}", badgeSlug, userId);
                // Return the existing record
                return userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId)
                        .stream()
                        .filter(ub -> ub.getBadge().getId().equals(badge.getId()))
                        .findFirst()
                        .map(ub -> toResponse(badge, ub))
                        .orElse(null);
            }

            UserBadge userBadge = new UserBadge(userId, badge);
            userBadgeRepository.save(userBadge);

            if (badge.getXpReward() > 0) {
                xpService.awardXp(userId, badge.getXpReward());
            }

            log.info("Admin awarded badge {} to userId={}", badgeSlug, userId);
            return toResponse(badge, userBadge);
        });
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private boolean evaluateCondition(
            Badge badge,
            UserXp userXp,
            String difficulty,
            boolean isFirstSolve,
            int streakDays,
            long executionTimeMs) {

        return switch (badge.getConditionType()) {
            case "FIRST_SOLVE" -> isFirstSolve && userXp.getTotalSolves() == 1;

            case "PROBLEMS_SOLVED" -> {
                int threshold = extractIntField(badge.getConditionValue(), "threshold");
                String requiredDifficulty = extractStringField(badge.getConditionValue(), "difficulty");

                if (requiredDifficulty == null) {
                    yield userXp.getTotalSolves() >= threshold;
                }
                int count = switch (requiredDifficulty.toUpperCase()) {
                    case "EASY"   -> userXp.getEasySolves();
                    case "MEDIUM" -> userXp.getMediumSolves();
                    case "HARD"   -> userXp.getHardSolves();
                    default       -> 0;
                };
                yield count >= threshold;
            }

            case "STREAK_DAYS" -> {
                int threshold = extractIntField(badge.getConditionValue(), "threshold");
                yield streakDays >= threshold;
            }

            case "TOTAL_SOLVES" -> {
                int threshold = extractIntField(badge.getConditionValue(), "threshold");
                yield userXp.getTotalSolves() >= threshold;
            }

            case "SPEED_SOLVE" -> {
                long maxMs = extractLongField(badge.getConditionValue(), "maxExecutionTimeMs");
                yield executionTimeMs > 0 && executionTimeMs <= maxMs;
            }

            default -> {
                log.warn("Unknown badge condition type: {}", badge.getConditionType());
                yield false;
            }
        };
    }

    /**
     * Naive JSON field extraction — avoids a full Jackson ObjectMapper dependency
     * for simple integer values stored in JSONB strings.
     * Format expected: {"key": 123, ...}
     */
    private int extractIntField(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\"";
            int idx = json.indexOf(pattern);
            if (idx == -1) return 0;
            int colonIdx = json.indexOf(":", idx);
            int end = json.indexOf(",", colonIdx);
            if (end == -1) end = json.indexOf("}", colonIdx);
            return Integer.parseInt(json.substring(colonIdx + 1, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long extractLongField(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\"";
            int idx = json.indexOf(pattern);
            if (idx == -1) return 0L;
            int colonIdx = json.indexOf(":", idx);
            int end = json.indexOf(",", colonIdx);
            if (end == -1) end = json.indexOf("}", colonIdx);
            return Long.parseLong(json.substring(colonIdx + 1, end).trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String extractStringField(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\"";
            int idx = json.indexOf(pattern);
            if (idx == -1) return null;
            int colonIdx = json.indexOf(":", idx);
            int openQuote = json.indexOf("\"", colonIdx + 1);
            int closeQuote = json.indexOf("\"", openQuote + 1);
            return json.substring(openQuote + 1, closeQuote);
        } catch (Exception e) {
            return null;
        }
    }

    private BadgeResponse toResponse(Badge badge, UserBadge userBadge) {
        return new BadgeResponse(
                badge.getId(),
                badge.getSlug(),
                badge.getName(),
                badge.getDescription(),
                badge.getIconUrl(),
                badge.getRarity().name(),
                badge.getXpReward(),
                userBadge.getEarnedAt()
        );
    }
}
