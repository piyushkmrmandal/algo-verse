package com.algoverse.auth.application.usecase;

import com.algoverse.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final JwtService jwtService;

    /**
     * Revokes the refresh token from Redis and blacklists the access token's jti.
     *
     * @param rawRefreshToken  composite token "{userId}:{tokenId}" (may be null)
     * @param accessTokenJti   jti claim from the current access token (may be null)
     * @param accessTokenExpiry expiry of the current access token (may be null)
     */
    public void execute(String rawRefreshToken, String accessTokenJti, Instant accessTokenExpiry) {
        log.debug("Processing logout");

        // Revoke refresh token in Redis
        if (rawRefreshToken != null && rawRefreshToken.contains(":")) {
            int sep = rawRefreshToken.indexOf(":");
            String userId = rawRefreshToken.substring(0, sep);
            String tokenId = rawRefreshToken.substring(sep + 1);
            jwtService.deleteRefreshToken(userId, tokenId);
            log.info("Refresh token revoked for userId: {}", userId);
        }

        // Blacklist the access token by jti
        if (accessTokenJti != null && accessTokenExpiry != null) {
            jwtService.blacklistToken(accessTokenJti, accessTokenExpiry);
            log.info("Access token jti={} blacklisted", accessTokenJti);
        }
    }
}
