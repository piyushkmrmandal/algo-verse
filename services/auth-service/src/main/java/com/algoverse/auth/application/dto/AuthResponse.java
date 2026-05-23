package com.algoverse.auth.application.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserDto user
) {
}
