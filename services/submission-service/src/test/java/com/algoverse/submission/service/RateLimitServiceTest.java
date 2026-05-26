package com.algoverse.submission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService unit tests")
class RateLimitServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private RateLimitService rateLimitService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String KEY = "ratelimit:submission:" + USER_ID;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Default config: 10 max requests, 60-second window
        ReflectionTestUtils.setField(rateLimitService, "maxRequests", 10);
        ReflectionTestUtils.setField(rateLimitService, "windowSeconds", 60L);
    }

    // ── Under rate limit ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("under rate limit")
    class UnderRateLimit {

        @Test
        @DisplayName("first request sets TTL on the key")
        void checkAndIncrement_firstRequest_setsExpiry() {
            when(valueOps.increment(KEY)).thenReturn(1L);

            assertThatNoException().isThrownBy(() -> rateLimitService.checkAndIncrement(USER_ID));

            verify(redisTemplate).expire(eq(KEY), eq(Duration.ofSeconds(60)));
        }

        @Test
        @DisplayName("subsequent requests within limit do not reset TTL")
        void checkAndIncrement_subsequentRequest_doesNotResetExpiry() {
            when(valueOps.increment(KEY)).thenReturn(5L); // not 1, so no expire call

            assertThatNoException().isThrownBy(() -> rateLimitService.checkAndIncrement(USER_ID));

            verify(redisTemplate, never()).expire(any(), any());
        }

        @Test
        @DisplayName("request at exactly maxRequests limit is allowed")
        void checkAndIncrement_atExactLimit_allowed() {
            when(valueOps.increment(KEY)).thenReturn(10L); // maxRequests = 10

            assertThatNoException().isThrownBy(() -> rateLimitService.checkAndIncrement(USER_ID));
        }
    }

    // ── Over rate limit ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("over rate limit")
    class OverRateLimit {

        @Test
        @DisplayName("exceeding limit throws 429 TooManyRequests")
        void checkAndIncrement_overLimit_throws429() {
            when(valueOps.increment(KEY)).thenReturn(11L); // 11 > 10

            assertThatThrownBy(() -> rateLimitService.checkAndIncrement(USER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(rse.getMessage()).contains("10");
                        assertThat(rse.getMessage()).contains("60");
                    });
        }

        @Test
        @DisplayName("well above limit also throws 429")
        void checkAndIncrement_wellAboveLimit_throws429() {
            when(valueOps.increment(KEY)).thenReturn(100L);

            assertThatThrownBy(() -> rateLimitService.checkAndIncrement(USER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode())
                                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        }
    }

    // ── Redis null handling ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Redis null handling")
    class RedisNullHandling {

        @Test
        @DisplayName("null Redis response fails open (no exception thrown)")
        void checkAndIncrement_redisReturnsNull_failsOpen() {
            when(valueOps.increment(KEY)).thenReturn(null);

            // Should not throw — fail open to avoid blocking legitimate users
            assertThatNoException().isThrownBy(() -> rateLimitService.checkAndIncrement(USER_ID));
        }
    }

    // ── getCurrentCount ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentCount")
    class GetCurrentCount {

        @Test
        @DisplayName("returns parsed count from Redis")
        void getCurrentCount_returnsCurrentValue() {
            when(valueOps.get(KEY)).thenReturn("7");

            long count = rateLimitService.getCurrentCount(USER_ID);

            assertThat(count).isEqualTo(7L);
        }

        @Test
        @DisplayName("returns 0 when key does not exist in Redis")
        void getCurrentCount_missingKey_returnsZero() {
            when(valueOps.get(KEY)).thenReturn(null);

            long count = rateLimitService.getCurrentCount(USER_ID);

            assertThat(count).isZero();
        }
    }

    // ── Key format ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis key contains the userId string")
    void checkAndIncrement_keyContainsUserId() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        rateLimitService.checkAndIncrement(USER_ID);

        verify(valueOps).increment(contains(USER_ID.toString()));
    }
}
