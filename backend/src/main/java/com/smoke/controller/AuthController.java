package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.ChangePasswordRequest;
import com.smoke.dto.LoginRequest;
import com.smoke.exception.BusinessException;
import com.smoke.security.ClientAddressResolver;
import com.smoke.security.LoginRateLimiter;
import com.smoke.service.AuthService;
import com.smoke.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientAddressResolver clientAddressResolver;

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientAddress = clientAddressResolver.resolve(servletRequest);
        loginRateLimiter.check(clientAddress);
        try {
            Result<?> response = Result.ok(authService.login(request));
            loginRateLimiter.recordSuccess(clientAddress);
            return response;
        } catch (BusinessException exception) {
            if (exception.getCode() == 401) {
                loginRateLimiter.recordFailure(clientAddress);
            }
            throw exception;
        }
    }

    @GetMapping("/me")
    public Result<?> me(Authentication authentication) {
        return Result.ok(authService.currentUser(authentication.getName()));
    }

    @PostMapping("/password")
    public Result<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changeOwnPassword(authentication.getName(), request);
        return Result.ok(null);
    }
}
