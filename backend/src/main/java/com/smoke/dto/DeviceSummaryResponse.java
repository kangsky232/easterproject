package com.smoke.dto;

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
        Integer latestConcentration,
        LocalDateTime latestTimestamp) {
}
