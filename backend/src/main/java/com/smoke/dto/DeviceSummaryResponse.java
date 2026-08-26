package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeviceSummaryResponse(
        Long id,
        String deviceId,
        String deviceName,
        String location,
        boolean online,
        Integer threshold,
        Integer battery,
        LocalDateTime lastHeartbeat,
        BigDecimal latestConcentration,
        BigDecimal latestTemperature,
        BigDecimal latestHumidity,
        BigDecimal latestCurrent,
        BigDecimal latestWireTemperature,
        BigDecimal latestCoValue,
        String latestBeepStatus,
        LocalDateTime latestTimestamp) {
}
