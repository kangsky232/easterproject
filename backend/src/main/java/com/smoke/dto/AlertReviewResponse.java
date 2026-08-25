package com.smoke.dto;

import java.time.LocalDateTime;

public record AlertReviewResponse(
        Long id,
        Long alertId,
        String reviewType,
        String reviewResult,
        String operatorName,
        LocalDateTime createdAt) {
}
