package com.smoke.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VisionEventResponse(
        Long id,
        String eventNo,
        String cameraCode,
        String location,
        String buildingCode,
        Integer floorNo,
        String frameKey,
        String imageUrl,
        String detectionMode,
        String modelName,
        String riskLevel,
        BigDecimal confidence,
        String summary,
        String evidence,
        String status,
        String dingtalkStatus,
        Integer dingtalkRecipients,
        String dingtalkError,
        String reviewerUsername,
        String reviewRemark,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
