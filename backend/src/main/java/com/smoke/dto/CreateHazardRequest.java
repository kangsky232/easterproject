package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateHazardRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 200) String location,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority) {
}
