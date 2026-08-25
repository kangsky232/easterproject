package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BindDeviceRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @NotBlank @Size(max = 100) String deviceName,
        @NotBlank @Size(max = 200) String location) {
}
