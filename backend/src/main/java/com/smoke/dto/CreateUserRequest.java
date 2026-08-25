package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 64) String displayName,
        @NotBlank
        @Pattern(regexp = "RESIDENT|COMMUNITY_ADMIN|SYSTEM_ADMIN|FIREFIGHTER") String role,
        @Size(max = 20) String phone) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username
                + ", password=[REDACTED], displayName=[REDACTED], role=" + role
                + ", phone=[REDACTED]]";
    }
}
