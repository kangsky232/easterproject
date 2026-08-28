package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MapDeviceResponse(
        Long id,
        String deviceId,
        String deviceName,
        String location,
        String buildingCode,
        String buildingName,
        Integer floorNo,
        String roomLabel,
        BigDecimal positionX,
        BigDecimal positionZ,
        boolean online,
        String status,
        String alertSeverity,
        Integer battery,
        BigDecimal smoke,
        BigDecimal temperature,
        BigDecimal humidity,
        BigDecimal current,
        BigDecimal wireTemperature,
        BigDecimal coValue,
        LocalDateTime latestTimestamp) {
}
