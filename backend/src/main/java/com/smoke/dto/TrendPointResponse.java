package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrendPointResponse(
        LocalDateTime bucketStart,
        BigDecimal average,
        BigDecimal minimum,
        BigDecimal maximum,
        long samples,
        BigDecimal averageTemperature,
        BigDecimal averageHumidity,
        BigDecimal averageCurrent,
        BigDecimal averageWireTemperature,
        BigDecimal averageCoValue) {
}
