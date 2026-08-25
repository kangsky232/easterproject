package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrendPointResponse(
        LocalDateTime bucketStart,
        BigDecimal average,
        int minimum,
        int maximum,
        long samples) {
}
