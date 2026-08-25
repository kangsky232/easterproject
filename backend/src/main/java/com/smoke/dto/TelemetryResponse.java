package com.smoke.dto;

import com.smoke.entity.AlertRecord;
import com.smoke.entity.SmokeData;

public record TelemetryResponse(
        boolean accepted,
        boolean duplicate,
        SmokeData record,
        boolean thresholdExceeded,
        AlertRecord alert) {
}
