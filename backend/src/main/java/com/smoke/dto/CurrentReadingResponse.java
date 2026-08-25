package com.smoke.dto;

import java.time.LocalDateTime;

public record CurrentReadingResponse(
        Long id,
        String deviceId,
        String deviceName,
        Integer concentration,
        LocalDateTime timestamp,
        Integer threshold,
        boolean online) {
}
