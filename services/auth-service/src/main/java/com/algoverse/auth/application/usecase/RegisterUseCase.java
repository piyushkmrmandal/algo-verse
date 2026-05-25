package com.algoverse.auth.application.usecase;

import com.algoverse.auth.application.dto.AuthResponse;
import com.algoverse.auth.application.dto.RegisterRequest;
import com.algoverse.auth.application.dto.UserDto;
import com.algoverse.auth.domain.exception.ConflictException;
import com.algoverse.auth.domain.model.User;
import com.algoverse.auth.domain.model.UserRole;
import com.algoverse.auth.domain.repository.UserRepository;
import com.algoverse.auth.infrastructure.kafka.UserEventProducer;
import com.algoverse.auth.infrastructure.security.JwtProperties;
import com.algoverse.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUseCase {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer userEventProducer;

    @Transactional
    public AuthResponse execute(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = User.builder()
                .email(request.email())
                .passwordHash(hashedPassword)
                .displayName(request.displayName())
                .role(UserRole.USER)
                .isEmailVerified(false)
                .isActive(true)
                .mfaEnabled(false)
                .build();

        user = userRepository.save(user);
        log.info("User created successfully with id: {}", user.getId());

        String rawAccessToken = jwtService.generateAccessToken(user);
        String refreshTokenId = jwtService.generateRefreshToken(user);
        // Composite token: userId:tokenId
        String rawRefreshToken = user.getId() + ":" + refreshTokenId;

        userEventProducer.publishUserRegistered(user);

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
