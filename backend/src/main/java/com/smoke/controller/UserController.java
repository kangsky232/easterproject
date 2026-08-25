package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.CreateUserRequest;
import com.smoke.dto.ResetPasswordRequest;
import com.smoke.dto.UpdateUserRequest;
import com.smoke.dto.UserStatusRequest;
import com.smoke.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer enabled) {
        return Result.ok(userService.list(page, pageSize, keyword, role, enabled));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(userService.get(id));
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request,
            Authentication authentication) {
        return Result.ok(userService.updateStatus(id, request.enabled(), authentication.getName()));
    }

    @PutMapping("/{id}")
    public Result<?> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return Result.ok(userService.update(id, request, authentication.getName()));
    }

    @PutMapping("/{id}/password")
    public Result<?> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request,
            Authentication authentication) {
        userService.resetPassword(id, request.password(), authentication.getName());
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
        return Result.ok(null);
    }
}
