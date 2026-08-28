package com.smoke.dto;

import java.math.BigDecimal;

public record MapPositionResponse(
        String deviceId,
        String buildingCode,
        Integer floorNo,
        String roomLabel,
        BigDecimal positionX,
        BigDecimal positionZ) {
}
