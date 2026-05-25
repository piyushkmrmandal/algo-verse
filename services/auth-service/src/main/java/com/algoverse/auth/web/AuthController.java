package com.algoverse.auth.web;

import com.algoverse.auth.application.dto.AuthResponse;
import com.algoverse.auth.application.dto.LoginRequest;
import com.algoverse.auth.application.dto.RefreshRequest;
import com.algoverse.auth.application.dto.RegisterRequest;
import com.algoverse.auth.application.dto.UserDto;
import com.algoverse.auth.application.usecase.LoginUseCase;
import com.algoverse.auth.application.usecase.LogoutUseCase;
import com.algoverse.auth.application.usecase.RefreshTokenUseCase;
import com.algoverse.auth.application.usecase.RegisterUseCase;
import com.algoverse.auth.domain.exception.UnauthorizedException;
import com.algoverse.auth.domain.model.User;
import com.algoverse.auth.domain.repository.UserRepository;
import com.algoverse.auth.infrastructure.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth endpoints for registration, login, token refresh, and logout")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Email already in use")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = registerUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login", description = "Authenticates a user and returns JWT tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account deactivated")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = loginUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh tokens", description = "Issues new access and refresh tokens using a valid refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = refreshTokenUseCase.execute(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout", description = "Revokes the refresh token and blocklists the current access token",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(
            @Valid @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        String jti = (String) httpRequest.getAttribute("jti");
        Instant accessTokenExpiry = (Instant) httpRequest.getAttribute("jwtExpiry");

        String rawRefreshToken = request != null ? request.refreshToken() : null;
        logoutUseCase.execute(rawRefreshToken, jti, accessTokenExpiry);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get current user", description = "Returns the authenticated user's profile",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(HttpServletRequest httpRequest) {
        UUID userId = (UUID) httpRequest.getAttribute("userId");

        if (userId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new UnauthorizedException("Not authenticated");
            }
            String email = (String) auth.getPrincipal();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            return ResponseEntity.ok(new UserDto(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.getRole().name(),
                    user.isEmailVerified()
            ));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return ResponseEntity.ok(new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.isEmailVerified()
        ));
    }
}
