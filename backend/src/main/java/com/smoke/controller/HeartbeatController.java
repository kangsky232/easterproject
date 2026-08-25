package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.HeartbeatRequest;
import com.smoke.security.DeviceRequestAuthentication;
import com.smoke.service.HeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;
    private final DeviceRequestAuthentication deviceRequestAuthentication;

    @PostMapping
    public Result<?> heartbeat(
            @Valid @RequestBody HeartbeatRequest request,
            @RequestHeader(name = "X-Device-Token", required = false) String deviceAccessToken) {
        deviceRequestAuthentication.verify(request.deviceId(), deviceAccessToken);
        return Result.ok(heartbeatService.heartbeat(request.deviceId(), request.battery()));
    }
}
