package com.algoverse.gamification.service;

import com.algoverse.gamification.domain.UserXp;
import com.algoverse.gamification.dto.LeaderboardEntry;
import com.algoverse.gamification.repository.UserXpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Leaderboard management using Redis sorted sets.
 *
 * <p>Redis key: {@code leaderboard:global}
 * <br>Member: userId (string)
 * <br>Score: totalXp (double)
 *
 * <p>On every XP award the score is updated via ZADD. Reads use ZREVRANGEBYSCORE
 * with pagination. If Redis is unavailable, the service falls back to a DB query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final String LEADERBOARD_KEY = "leaderboard:global";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserXpRepository userXpRepository;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns a paginated slice of the global leaderboard ordered by XP descending.
     *
     * @param page zero-based page index
     * @param size number of entries per page
     * @return leaderboard entries with rank, userId, displayName, totalXp, level
     */
    public List<LeaderboardEntry> getLeaderboard(int page, int size) {
        try {
            return getFromRedis(page, size);
        } catch (Exception e) {
            log.warn("Redis leaderboard unavailable, falling back to DB: {}", e.getMessage());
            return getFromDb(page, size);
        }
    }

    /**
     * Updates a user's score in the Redis sorted set. Called on every XP award.
     *
     * @param userId      the user whose score changed
     * @param totalXp     the user's new total XP
     * @param displayName display name for leaderboard rendering
     */
    public void updateScore(UUID userId, int totalXp, String displayName) {
        try {
            String member = userId.toString();
            redisTemplate.opsForZSet().add(LEADERBOARD_KEY, member, totalXp);
            log.debug("Leaderboard updated: userId={} newScore={}", userId, totalXp);
        } catch (Exception e) {
            log.warn("Failed to update leaderboard score for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Returns the current rank of a user (1-indexed), or -1 if not ranked.
     */
    public long getRank(UUID userId) {
        try {
            Long rank = redisTemplate.opsForZSet()
                    .reverseRank(LEADERBOARD_KEY, userId.toString());
            return rank != null ? rank + 1 : -1L;
        } catch (Exception e) {
            log.warn("Failed to get rank for userId={}: {}", userId, e.getMessage());
            return -1L;
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<LeaderboardEntry> getFromRedis(int page, int size) {
        long start = (long) page * size;
        long end   = start + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<LeaderboardEntry> entries = new ArrayList<>(tuples.size());
        long rank = start + 1;

        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            int score     = tuple.getScore() != null ? tuple.getScore().intValue() : 0;

            UUID userId   = parseUUID(member);
            String name   = resolveDisplayName(userId);
            int level     = UserXp.computeLevel(score);

            entries.add(new LeaderboardEntry(rank++, userId, name, score, level));
        }

        return entries;
    }

    private List<LeaderboardEntry> getFromDb(int page, int size) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size);

        return userXpRepository.findAllByOrderByTotalXpDesc(pageable)
                .getContent()
                .stream()
                .map(ux -> {
                    long rank = (long) page * size + 1;
                    String name = ux.getDisplayName() != null ? ux.getDisplayName() : ux.getUserId().toString();
                    return new LeaderboardEntry(rank, ux.getUserId(), name, ux.getTotalXp(), ux.getLevel());
                })
                .toList();
    }

    private String resolveDisplayName(UUID userId) {
        if (userId == null) return "Unknown";
        return userXpRepository.findById(userId)
                .map(ux -> ux.getDisplayName() != null ? ux.getDisplayName() : ux.getUserId().toString())
                .orElse(userId.toString());
    }

    private static UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            return UUID.randomUUID(); // should never happen
        }
    }
}
