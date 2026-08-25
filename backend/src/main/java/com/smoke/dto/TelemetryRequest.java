package com.smoke.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TelemetryRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotNull @Min(0) @Max(1_000_000) Integer concentration,
        @Size(max = 64) String messageId,
        @PastOrPresent LocalDateTime timestamp) {
}
