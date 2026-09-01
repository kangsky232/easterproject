package com.smoke.service;

record SimulatedVisionFrame(
        String frameKey,
        String cameraCode,
        String location,
        String buildingCode,
        int floorNo,
        String imageUrl,
        boolean expectedSuspicious,
        String fallbackSummary,
        String fallbackEvidence) {
}
