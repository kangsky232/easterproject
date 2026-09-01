package com.smoke.dto;

import java.time.LocalDateTime;

public record VisionFrameResponse(
        String frameKey,
        String cameraCode,
        String location,
        String buildingCode,
        int floorNo,
        String imageUrl,
        LocalDateTime capturedAt) {
}
