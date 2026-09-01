package com.smoke.dto;

public record VisionSummaryResponse(
        long pendingReview,
        long confirmedFire,
        long falseAlarm,
        long total) {
}
