package com.smoke.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TelemetryRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000000.0") BigDecimal concentration,
        @Size(max = 64) String messageId,
        @PastOrPresent LocalDateTime timestamp) {
}
