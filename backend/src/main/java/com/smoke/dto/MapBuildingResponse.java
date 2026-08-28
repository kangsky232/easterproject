package com.smoke.dto;

import java.math.BigDecimal;

public record MapBuildingResponse(
        String buildingCode,
        String buildingName,
        BigDecimal positionX,
        BigDecimal positionZ,
        BigDecimal width,
        BigDecimal depth,
        Integer floors) {
}
