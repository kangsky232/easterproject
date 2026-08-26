package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CurrentReadingResponse(
        Long id,
        String deviceId,
        String deviceName,
        BigDecimal concentration,
        BigDecimal temperature,
        BigDecimal humidity,
        BigDecimal current,
        BigDecimal wireTemperature,
        BigDecimal coValue,
        String beepStatus,
        LocalDateTime timestamp,
        Integer threshold,
        boolean online) {
}
