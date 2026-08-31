package com.smoke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationAuditRequest(
        @NotBlank
        @Pattern(regexp = "NORMAL|FOLLOWED_UP", message = "result 仅支持 NORMAL 或 FOLLOWED_UP")
        String result,
        @NotBlank(message = "请填写核查结论")
        @Size(max = 500, message = "核查结论不能超过 500 个字符")
        String remark) {
}
