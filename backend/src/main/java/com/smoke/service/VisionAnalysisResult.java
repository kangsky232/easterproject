package com.smoke.service;

record VisionAnalysisResult(
        boolean suspectedFire,
        double confidence,
        String riskLevel,
        String summary,
        String evidence,
        String mode,
        String model,
        String error) {
}
