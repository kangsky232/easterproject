package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String password) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[password=[REDACTED]]";
    }
}
