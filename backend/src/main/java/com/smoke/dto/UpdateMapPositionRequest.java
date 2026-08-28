package com.smoke.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateMapPositionRequest(
        @NotBlank @Size(max = 32) String buildingCode,
        @NotNull @Min(1) Integer floorNo,
        @NotBlank @Size(max = 64) String roomLabel,
        @NotNull @DecimalMin("0.0") @Digits(integer = 6, fraction = 2) BigDecimal positionX,
        @NotNull @DecimalMin("0.0") @Digits(integer = 6, fraction = 2) BigDecimal positionZ) {
}
