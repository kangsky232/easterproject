package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.NotificationResponse;
import com.smoke.dto.NotificationSummaryResponse;
import com.smoke.dto.PageResponse;
import com.smoke.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false) String status) {
        return Result.ok(notificationService.list(page, pageSize, alertId, deviceId, channel, status));
    }

    @GetMapping("/summary")
    public Result<NotificationSummaryResponse> summary() {
        return Result.ok(notificationService.summary());
    }

    @GetMapping("/{id}")
    public Result<NotificationResponse> get(@PathVariable Long id) {
        return Result.ok(notificationService.get(id));
    }
}
