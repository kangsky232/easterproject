package com.smoke.dto;

public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        String role) {
}
