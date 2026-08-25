package com.smoke.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BroadcastStatusRequest(
        @NotNull @Min(1) @Max(2) Integer status) {
}
