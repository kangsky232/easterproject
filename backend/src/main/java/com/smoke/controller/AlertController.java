package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.AlertReviewResponse;
import com.smoke.service.AlertReviewService;
import com.smoke.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AlertReviewService alertReviewService;

    @Autowired
    public AlertController(AlertService alertService, AlertReviewService alertReviewService) {
        this.alertService = alertService;
        this.alertReviewService = alertReviewService;
    }

    AlertController(AlertService alertService) {
        this(alertService, null);
    }

    @GetMapping
    public Result<?> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(alertService.list(deviceId, type, status, page, pageSize));
    }

    @PostMapping("/{id}/confirm")
    public Result<?> confirm(@PathVariable Long id, Authentication authentication) {
        return Result.ok(alertService.confirm(id, authentication.getName()));
    }

    @PostMapping("/{id}/resolve")
    public Result<?> resolve(@PathVariable Long id, Authentication authentication) {
        return Result.ok(alertService.resolve(id, authentication.getName()));
    }

    @PostMapping("/{id}/false-alarm")
    public Result<?> falseAlarm(@PathVariable Long id, Authentication authentication) {
        return Result.ok(alertService.markFalseAlarm(id, authentication.getName()));
    }

    @PostMapping("/{id}/verify")
    public Result<AlertReviewResponse> verify(@PathVariable Long id, Authentication authentication) {
        return Result.ok(alertReviewService.review(id, authentication.getName()));
    }
}
