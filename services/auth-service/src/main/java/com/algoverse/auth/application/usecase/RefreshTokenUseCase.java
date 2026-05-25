package com.algoverse.auth.application.usecase;

import com.algoverse.auth.application.dto.AuthResponse;
import com.algoverse.auth.application.dto.UserDto;
import com.algoverse.auth.domain.exception.UnauthorizedException;
import com.algoverse.auth.domain.model.User;
import com.algoverse.auth.domain.repository.UserRepository;
import com.algoverse.auth.infrastructure.security.JwtProperties;
import com.algoverse.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Validates a refresh token stored in Redis, issues new access + refresh tokens
 * (token rotation), and invalidates the old refresh token.
 *
 * Token format expected by client: "{userId}:{tokenId}"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse execute(String rawRefreshToken) {
        log.debug("Processing refresh token rotation");

        // Token is opaque UUID stored in Redis under refresh:{userId}:{tokenId}
        // The client sends just the tokenId; we need the userId too.
        // To allow stateless lookup we store userId as the Redis value.
        // Client must send composite token: userId:tokenId
        if (rawRefreshToken == null || !rawRefreshToken.contains(":")) {
            throw new UnauthorizedException("Invalid refresh token format");
        }

        int separatorIdx = rawRefreshToken.indexOf(":");
        String userId = rawRefreshToken.substring(0, separatorIdx);
        String tokenId = rawRefreshToken.substring(separatorIdx + 1);

        String storedUserId = jwtService.validateRefreshToken(userId, tokenId);
        if (storedUserId == null) {
            log.warn("Refresh token not found or expired for userId: {}", userId);
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Invalidate old token (rotation)
        jwtService.deleteRefreshToken(userId, tokenId);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Associated user not found"));

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenId = jwtService.generateRefreshToken(user);
        // Return composite token for the client
        String newRefreshToken = user.getId() + ":" + newRefreshTokenId;

        log.info("Refresh token rotated successfully for userId: {}", user.getId());

        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.isEmailVerified()
        );

        return new AuthResponse(newAccessToken, newRefreshToken, jwtProperties.getAccessTokenExpiry(), userDto);
    }
}
