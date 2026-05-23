package com.algoverse.auth.application.usecase;

import com.algoverse.auth.application.dto.AuthResponse;
import com.algoverse.auth.application.dto.UserDto;
import com.algoverse.auth.domain.exception.UnauthorizedException;
import com.algoverse.auth.domain.model.RefreshToken;
import com.algoverse.auth.domain.model.User;
import com.algoverse.auth.domain.repository.RefreshTokenRepository;
import com.algoverse.auth.domain.repository.UserRepository;
import com.algoverse.auth.infrastructure.security.JwtProperties;
import com.algoverse.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse execute(String rawRefreshToken) {
        log.debug("Processing refresh token rotation");

        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        RefreshToken existingToken = allTokens.stream()
                .filter(t -> passwordEncoder.matches(rawRefreshToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Refresh token not found or invalid");
                    return new UnauthorizedException("Invalid or expired refresh token");
                });

        if (existingToken.getRevokedAt() != null) {
            log.warn("Attempted use of revoked refresh token for userId: {}", existingToken.getUserId());
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (existingToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Attempted use of expired refresh token for userId: {}", existingToken.getUserId());
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = userRepository.findById(existingToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Associated user not found"));

        existingToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existingToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRawRefreshToken = jwtService.generateRefreshToken();
        String newHashedRefreshToken = passwordEncoder.encode(newRawRefreshToken);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .tokenHash(newHashedRefreshToken)
                .userId(user.getId())
                .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpiry()))
                .build();

        refreshTokenRepository.save(newRefreshToken);
        log.info("Refresh token rotated successfully for userId: {}", user.getId());

        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.isEmailVerified()
        );

        return new AuthResponse(newAccessToken, newRawRefreshToken, jwtProperties.getAccessTokenExpiry(), userDto);
    }
}
