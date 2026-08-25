package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.TelemetryRequest;
import com.smoke.security.DeviceRequestAuthentication;
import com.smoke.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final DeviceRequestAuthentication deviceRequestAuthentication;

    @PostMapping
    public Result<?> report(
            @Valid @RequestBody TelemetryRequest request,
            @RequestHeader(name = "X-Device-Token", required = false) String deviceAccessToken) {
        deviceRequestAuthentication.verify(request.deviceId(), deviceAccessToken);
        return Result.ok(telemetryService.record(request));
    }
}
