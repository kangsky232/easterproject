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
        @DecimalMin("-1000.0") @DecimalMax("1000.0") BigDecimal temperature,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal humidity,
        @DecimalMin("0.0") @DecimalMax("1000000.0") BigDecimal current,
        @DecimalMin("-1000.0") @DecimalMax("1000.0") BigDecimal wireTemperature,
        @DecimalMin("0.0") @DecimalMax("1000000.0") BigDecimal coValue,
        @Size(max = 16) String beepStatus,
        @Size(max = 64) String messageId,
        @PastOrPresent LocalDateTime timestamp) {

    public TelemetryRequest(
            String deviceId, BigDecimal concentration, String messageId, LocalDateTime timestamp) {
        this(deviceId, concentration, null, null, null, null, null, null, messageId, timestamp);
    }
}
