package com.smoke.dto;

import java.time.LocalDateTime;

public record VisionAnalysisResponse(
        boolean suspectedFire,
        double confidence,
        String riskLevel,
        String summary,
        String evidence,
        String mode,
        String model,
        String error,
        LocalDateTime analyzedAt) {
}
