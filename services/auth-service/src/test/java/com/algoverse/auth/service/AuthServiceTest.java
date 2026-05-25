package com.algoverse.auth.service;

import com.algoverse.auth.application.dto.AuthResponse;
import com.algoverse.auth.application.dto.LoginRequest;
import com.algoverse.auth.application.dto.RegisterRequest;
import com.algoverse.auth.application.usecase.LoginUseCase;
import com.algoverse.auth.application.usecase.LogoutUseCase;
import com.algoverse.auth.application.usecase.RefreshTokenUseCase;
import com.algoverse.auth.application.usecase.RegisterUseCase;
import com.algoverse.auth.domain.exception.ConflictException;
import com.algoverse.auth.domain.exception.UnauthorizedException;
import com.algoverse.auth.domain.model.User;
import com.algoverse.auth.domain.model.UserRole;
import com.algoverse.auth.domain.repository.UserRepository;
import com.algoverse.auth.infrastructure.kafka.UserEventProducer;
import com.algoverse.auth.infrastructure.security.JwtProperties;
import com.algoverse.auth.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Service Unit Tests")
class AuthServiceTest {

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@algoverse.io";
    private static final String PASSWORD = "P@ssw0rd!";
    private static final String HASHED_PASSWORD = "$2a$12$hashedPasswordValue";
    private static final String DISPLAY_NAME = "Test User";
    private static final String ACCESS_TOKEN = "eyJhbGciOiJSUzI1NiJ9.access.token";
    private static final String REFRESH_TOKEN_ID = UUID.randomUUID().toString();
    private static final String REFRESH_TOKEN = USER_ID + ":" + REFRESH_TOKEN_ID;
    private static final long EXPIRY_SECONDS = 900L;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .displayName(DISPLAY_NAME)
                .role(UserRole.USER)
                .isActive(true)
                .build();
    }

    // =========================================================================
    // RegisterUseCase tests
    // =========================================================================

    @Nested
    @DisplayName("RegisterUseCase")
    class RegisterUseCaseTests {

        @Mock
        private UserRepository userRepository;

        @Mock
        private JwtService jwtService;

        @Mock
        private JwtProperties jwtProperties;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private UserEventProducer userEventProducer;

        @InjectMocks
        private RegisterUseCase registerUseCase;

        @Test
        @DisplayName("register_newUser_returnsAuthResponse")
        void register_newUser_returnsAuthResponse() {
            RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, DISPLAY_NAME);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(activeUser);
            when(jwtService.generateAccessToken(any(User.class))).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn(REFRESH_TOKEN_ID);
            when(jwtProperties.getAccessTokenExpiry()).thenReturn(EXPIRY_SECONDS);

            AuthResponse response = registerUseCase.execute(request);

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).contains(REFRESH_TOKEN_ID);
            assertThat(response.user().email()).isEqualTo(EMAIL);
            assertThat(response.user().role()).isEqualTo("USER");

            verify(userRepository).save(any(User.class));
            verify(userEventProducer).publishUserRegistered(any(User.class));
        }

        @Test
        @DisplayName("register_duplicateEmail_throwsConflict")
        void register_duplicateEmail_throwsConflict() {
            RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, DISPLAY_NAME);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> registerUseCase.execute(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already in use");

            verify(userRepository, never()).save(any());
            verify(userEventProducer, never()).publishUserRegistered(any());
        }
    }

    // =========================================================================
    // LoginUseCase tests
    // =========================================================================

    @Nested
    @DisplayName("LoginUseCase")
    class LoginUseCaseTests {

        @Mock
        private UserRepository userRepository;

        @Mock
        private JwtService jwtService;

        @Mock
        private JwtProperties jwtProperties;

        @Mock
        private PasswordEncoder passwordEncoder;

        @InjectMocks
        private LoginUseCase loginUseCase;

        @Test
        @DisplayName("login_validCredentials_returnsAuthResponse")
        void login_validCredentials_returnsAuthResponse() {
            LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(jwtService.generateAccessToken(activeUser)).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken(activeUser)).thenReturn(REFRESH_TOKEN_ID);
            when(jwtProperties.getAccessTokenExpiry()).thenReturn(EXPIRY_SECONDS);

            AuthResponse response = loginUseCase.execute(request);

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(USER_ID + ":" + REFRESH_TOKEN_ID);
        }

        @Test
        @DisplayName("login_wrongPassword_throwsUnauthorized")
        void login_wrongPassword_throwsUnauthorized() {
            LoginRequest request = new LoginRequest(EMAIL, "wrongPassword");

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPassword", HASHED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> loginUseCase.execute(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("login_unknownEmail_throwsUnauthorized")
        void login_unknownEmail_throwsUnauthorized() {
            LoginRequest request = new LoginRequest("unknown@example.com", PASSWORD);

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loginUseCase.execute(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid email or password");
        }
    }

    // =========================================================================
    // RefreshTokenUseCase tests
    // =========================================================================

    @Nested
    @DisplayName("RefreshTokenUseCase")
    class RefreshTokenUseCaseTests {

        @Mock
        private UserRepository userRepository;

        @Mock
        private JwtService jwtService;

        @Mock
        private JwtProperties jwtProperties;

        @InjectMocks
        private RefreshTokenUseCase refreshTokenUseCase;

        @Test
        @DisplayName("refresh_validToken_returnsNewTokens")
        void refresh_validToken_returnsNewTokens() {
            String newRefreshTokenId = UUID.randomUUID().toString();

            when(jwtService.validateRefreshToken(USER_ID.toString(), REFRESH_TOKEN_ID))
                    .thenReturn(USER_ID.toString());
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
            when(jwtService.generateAccessToken(activeUser)).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken(activeUser)).thenReturn(newRefreshTokenId);
            when(jwtProperties.getAccessTokenExpiry()).thenReturn(EXPIRY_SECONDS);

            AuthResponse response = refreshTokenUseCase.execute(REFRESH_TOKEN);

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).startsWith(USER_ID + ":");

            verify(jwtService).deleteRefreshToken(USER_ID.toString(), REFRESH_TOKEN_ID);
        }

        @Test
        @DisplayName("refresh_invalidToken_throwsUnauthorized")
        void refresh_invalidToken_throwsUnauthorized() {
            when(jwtService.validateRefreshToken(anyString(), anyString())).thenReturn(null);

            assertThatThrownBy(() -> refreshTokenUseCase.execute(REFRESH_TOKEN))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid or expired refresh token");
        }
    }

    // =========================================================================
    // LogoutUseCase tests
    // =========================================================================

    @Nested
    @DisplayName("LogoutUseCase")
    class LogoutUseCaseTests {

        @Mock
        private JwtService jwtService;

        @InjectMocks
        private LogoutUseCase logoutUseCase;

        @Test
        @DisplayName("logout_validToken_blacklistsToken")
        void logout_validToken_blacklistsToken() {
            String jti = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plusSeconds(900);

            logoutUseCase.execute(REFRESH_TOKEN, jti, expiry);

            verify(jwtService).deleteRefreshToken(USER_ID.toString(), REFRESH_TOKEN_ID);
            verify(jwtService).blacklistToken(eq(jti), eq(expiry));
        }

        @Test
        @DisplayName("logout_nullTokens_doesNotThrow")
        void logout_nullTokens_doesNotThrow() {
            logoutUseCase.execute(null, null, null);

            verifyNoInteractions(jwtService);
        }
    }
}
