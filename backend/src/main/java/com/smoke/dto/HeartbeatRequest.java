package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record HeartbeatRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @Min(0) @Max(100) Integer battery) {
}
