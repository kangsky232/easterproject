package com.smoke.dto;

public record NotificationSummaryResponse(
        long total,
        long appCount,
        long smsCount,
        long dingTalkCount,
        long pendingCount,
        long sentCount,
        long failedCount) {
}
