package com.smoke.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String role,
        boolean enabled,
        String phone,
        LocalDateTime createdAt) {
}
