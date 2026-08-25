package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 64) String displayName,
        @NotBlank
        @Pattern(regexp = "RESIDENT|COMMUNITY_ADMIN|SYSTEM_ADMIN|FIREFIGHTER") String role,
        @Size(max = 20) String phone) {
}
