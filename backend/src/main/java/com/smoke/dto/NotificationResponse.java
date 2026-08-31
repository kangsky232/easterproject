package com.smoke.dto;

import java.time.LocalDateTime;

/** Stable notification contract for web and mobile clients. */
public record NotificationResponse(
        Long id,
        Long alertId,
        String deviceId,
        String channel,
        String receiver,
        String content,
        String status,
        LocalDateTime sentAt,
        String auditStatus,
        String auditResult,
        String auditorUsername,
        String auditRemark,
        LocalDateTime auditedAt,
        LocalDateTime createdAt) {
}
