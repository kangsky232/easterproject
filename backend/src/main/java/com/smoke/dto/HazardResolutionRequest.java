package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HazardResolutionRequest(
        @NotBlank @Size(max = 1000) String resolution) {
}
