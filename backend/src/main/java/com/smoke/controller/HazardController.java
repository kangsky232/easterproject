package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.CreateHazardRequest;
import com.smoke.dto.HazardResolutionRequest;
import com.smoke.dto.HazardReviewRequest;
import com.smoke.service.HazardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hazards")
@RequiredArgsConstructor
public class HazardController {

    private final HazardService hazardService;

    @GetMapping
    public Result<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            Authentication authentication) {
        return Result.ok(hazardService.list(
                authentication.getName(), role(authentication), status, priority, page, pageSize));
    }

    @GetMapping("/summary")
    public Result<?> summary(Authentication authentication) {
        return Result.ok(hazardService.summary(authentication.getName(), role(authentication)));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable Long id, Authentication authentication) {
        return Result.ok(hazardService.get(id, authentication.getName(), role(authentication)));
    }

    @PostMapping
    public Result<?> create(
            @Valid @RequestBody CreateHazardRequest request,
            Authentication authentication) {
        return Result.ok(hazardService.create(request, authentication.getName()));
    }

    @PostMapping("/{id}/claim")
    public Result<?> claim(@PathVariable Long id, Authentication authentication) {
        return Result.ok(hazardService.claim(id, authentication.getName(), role(authentication)));
    }

    @PostMapping("/{id}/submit")
    public Result<?> submit(
            @PathVariable Long id,
            @Valid @RequestBody HazardResolutionRequest request,
            Authentication authentication) {
        return Result.ok(hazardService.submitResolution(
                id, request.resolution(), authentication.getName(), role(authentication)));
    }

    @PostMapping("/{id}/review")
    public Result<?> review(
            @PathVariable Long id,
            @Valid @RequestBody HazardReviewRequest request,
            Authentication authentication) {
        return Result.ok(hazardService.review(
                id, request.approved(), request.remark(), authentication.getName(), role(authentication)));
    }

    private String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElse("RESIDENT");
    }
}
