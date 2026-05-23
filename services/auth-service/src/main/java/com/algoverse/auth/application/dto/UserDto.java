package com.algoverse.auth.application.dto;

import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String role,
        boolean isEmailVerified
) {
}
