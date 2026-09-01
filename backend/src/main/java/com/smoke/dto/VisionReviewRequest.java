package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VisionReviewRequest(
        @NotBlank
        @Pattern(regexp = "CONFIRMED_FIRE|FALSE_ALARM", message = "verdict 仅支持 CONFIRMED_FIRE 或 FALSE_ALARM")
        String verdict,
        @NotBlank(message = "请填写人工判断依据")
        @Size(max = 500, message = "人工判断依据不能超过 500 个字符")
        String remark) {
}
