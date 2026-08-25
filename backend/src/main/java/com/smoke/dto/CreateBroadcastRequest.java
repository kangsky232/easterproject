package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBroadcastRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotBlank @Size(max = 500) String content,
        Long triggerAlertId) {
}
