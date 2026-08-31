package com.smoke.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HazardReviewRequest(
        @NotNull Boolean approved,
        @Size(max = 1000) String remark) {
}
