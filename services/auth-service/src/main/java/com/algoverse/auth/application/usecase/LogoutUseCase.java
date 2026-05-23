package com.algoverse.auth.application.usecase;

import com.algoverse.auth.domain.model.RefreshToken;
import com.algoverse.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private static final String BLOCKLIST_PREFIX = "blocklist:jti:";

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public void execute(String rawRefreshToken, String accessTokenJti, Instant accessTokenExpiry) {
        log.debug("Processing logout");

        if (rawRefreshToken != null) {
            List<RefreshToken> allTokens = refreshTokenRepository.findAll();
            allTokens.stream()
                    .filter(t -> t.getRevokedAt() == null && passwordEncoder.matches(rawRefreshToken, t.getTokenHash()))
                    .findFirst()
                    .ifPresentOrElse(
                            token -> {
                                token.setRevokedAt(Instant.now());
                                refreshTokenRepository.save(token);
                                log.info("Refresh token revoked for userId: {}", token.getUserId());
                            },
                            () -> log.warn("Refresh token not found during logout")
                    );
        }

        if (accessTokenJti != null && accessTokenExpiry != null) {
            long ttlSeconds = ChronoUnit.SECONDS.between(Instant.now(), accessTokenExpiry);
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(
                        BLOCKLIST_PREFIX + accessTokenJti,
                        "revoked",
                        ttlSeconds,
                        TimeUnit.SECONDS
                );
                log.info("Access token jti={} added to blocklist with TTL={}s", accessTokenJti, ttlSeconds);
            }
        }
    }
}
