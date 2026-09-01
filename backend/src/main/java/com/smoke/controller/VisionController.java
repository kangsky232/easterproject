package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.VisionReviewRequest;
import com.smoke.service.VisionPatrolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
public class VisionController {

    private final VisionPatrolService visionPatrolService;

    @GetMapping("/status")
    public Result<?> status() {
        return Result.ok(visionPatrolService.status());
    }

    @GetMapping("/events")
    public Result<?> events(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(visionPatrolService.list(status, page, pageSize));
    }

    @GetMapping("/summary")
    public Result<?> summary() {
        return Result.ok(visionPatrolService.summary());
    }

    @PostMapping("/simulation/next")
    public Result<?> nextFrame() {
        return Result.ok(visionPatrolService.analyzeNextFrame());
    }

    @PostMapping("/patrol/start")
    public Result<?> startPatrol() {
        return Result.ok(visionPatrolService.startPatrol());
    }

    @PostMapping("/patrol/pause")
    public Result<?> pausePatrol() {
        return Result.ok(visionPatrolService.pausePatrol());
    }

    @PostMapping("/events/{id}/review")
    public Result<?> review(
            @PathVariable Long id,
            @Valid @RequestBody VisionReviewRequest request,
            Authentication authentication) {
        return Result.ok(visionPatrolService.review(
                id, request.verdict(), request.remark(), authentication.getName()));
    }
}
