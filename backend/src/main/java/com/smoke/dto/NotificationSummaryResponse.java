package com.smoke.dto;

public record NotificationSummaryResponse(
        long total,
        long appCount,
        long smsCount,
        long pendingCount,
        long sentCount,
        long failedCount) {
}
