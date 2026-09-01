package com.smoke.dto;

public record VisionStatusResponse(
        boolean enabled,
        boolean scanning,
        boolean deepSeekConfigured,
        String mode,
        String provider,
        String model,
        long intervalMs,
        double confidenceThreshold,
        VisionFrameResponse currentFrame,
        VisionAnalysisResponse latestAnalysis,
        VisionEventResponse latestEvent) {
}
