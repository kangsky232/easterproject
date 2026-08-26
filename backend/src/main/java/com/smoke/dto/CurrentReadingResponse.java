package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CurrentReadingResponse(
        Long id,
        String deviceId,
        String deviceName,
        BigDecimal concentration,
        LocalDateTime timestamp,
        Integer threshold,
        boolean online) {
}
