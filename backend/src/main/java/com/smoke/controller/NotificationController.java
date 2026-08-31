package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.NotificationAuditRequest;
import com.smoke.dto.NotificationResponse;
import com.smoke.dto.NotificationSummaryResponse;
import com.smoke.dto.PageResponse;
import com.smoke.service.NotificationService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) Long alertId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus) {
        return Result.ok(notificationService.list(page, pageSize, alertId, deviceId, channel, status, auditStatus));
    }

    @GetMapping("/summary")
    public Result<NotificationSummaryResponse> summary() {
        return Result.ok(notificationService.summary());
    }

    @GetMapping("/{id}")
    public Result<NotificationResponse> get(@PathVariable Long id) {
        return Result.ok(notificationService.get(id));
    }

    @PostMapping("/{id}/audit")
    public Result<NotificationResponse> audit(
            @PathVariable Long id,
            @Valid @RequestBody NotificationAuditRequest request,
            Authentication authentication) {
        return Result.ok(notificationService.audit(
                id, request.result(), request.remark(), authentication.getName()));
    }
}
