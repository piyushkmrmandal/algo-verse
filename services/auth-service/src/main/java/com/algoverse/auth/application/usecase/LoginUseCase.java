package com.algoverse.auth.application.usecase;

import com.algoverse.auth.application.dto.AuthResponse;
import com.algoverse.auth.application.dto.LoginRequest;
import com.algoverse.auth.application.dto.UserDto;
import com.algoverse.auth.domain.exception.ForbiddenException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse execute(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", request.email());
                    return new UnauthorizedException("Invalid email or password");
                });

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed - password mismatch for user: {}", user.getId());
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            log.warn("Login attempt by inactive user: {}", user.getId());
            throw new ForbiddenException("Account is deactivated. Please contact support.");
        }

        String rawAccessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();
        String hashedRefreshToken = passwordEncoder.encode(rawRefreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .tokenHash(hashedRefreshToken)
                .userId(user.getId())
                .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpiry()))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);
        log.info("User logged in successfully: {}", user.getId());

        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.isEmailVerified()
        );

        return new AuthResponse(rawAccessToken, rawRefreshToken, jwtProperties.getAccessTokenExpiry(), userDto);
    }
}
