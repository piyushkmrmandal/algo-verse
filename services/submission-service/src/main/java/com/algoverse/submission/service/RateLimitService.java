package com.algoverse.submission.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed sliding-window rate limiter for submissions.
 * Key: ratelimit:submission:{userId}
 * Max 10 submissions per 60-second window per user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${submission.rate-limit.max-requests:10}")
    private int maxRequests;

    @Value("${submission.rate-limit.window-seconds:60}")
    private long windowSeconds;

    private static final String KEY_PREFIX = "ratelimit:submission:";

    /**
     * Increments the submission counter for the given user.
     * Throws 429 TooManyRequests if the limit is exceeded.
     *
     * @param userId the authenticated user's UUID
     */
    public void checkAndIncrement(UUID userId) {
        String key = KEY_PREFIX + userId.toString();

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            // Redis returned null; fail open to avoid blocking legitimate users
            log.warn("RateLimitService: Redis returned null for key {}; failing open", key);
            return;
        }

        if (count == 1) {
            // First request in the window — set expiry
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count > maxRequests) {
            log.warn("RateLimitService: user {} exceeded submission rate limit ({}/{})",
                    userId, count, maxRequests);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Submission rate limit exceeded. Max " + maxRequests +
                    " submissions per " + windowSeconds + " seconds."
            );
        }

        log.debug("RateLimitService: user {} submission count {}/{}", userId, count, maxRequests);
    }

    /**
     * Returns the current submission count for a user (for observability / tests).
     */
    public long getCurrentCount(UUID userId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + userId.toString());
        return value == null ? 0L : Long.parseLong(value);
    }
}
