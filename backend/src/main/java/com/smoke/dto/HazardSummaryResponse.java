package com.smoke.dto;

public record HazardSummaryResponse(
        long reported,
        long processing,
        long pendingReview,
        long closed,
        long openTotal) {
}
