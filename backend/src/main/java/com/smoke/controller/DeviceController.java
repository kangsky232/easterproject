package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.BindDeviceRequest;
import com.smoke.dto.ThresholdRequest;
import com.smoke.dto.UpdateDeviceRequest;
import com.smoke.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public Result<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(deviceService.list(keyword, status, page, pageSize));
    }

    @PostMapping("/bind")
    public Result<?> bind(@Valid @RequestBody BindDeviceRequest request) {
        return Result.ok(deviceService.bind(request));
    }

    @PostMapping("/{id}/credentials")
    public Result<?> rotateCredentials(@PathVariable Long id) {
        return Result.ok(deviceService.rotateAccessToken(id));
    }

    @DeleteMapping("/{id}")
    public Result<?> unbind(@PathVariable Long id) {
        deviceService.unbind(id);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(deviceService.get(id));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody UpdateDeviceRequest request) {
        return Result.ok(deviceService.update(id, request));
    }

    @GetMapping("/{id}/current")
    public Result<?> current(@PathVariable Long id) {
        return Result.ok(deviceService.current(id));
    }

    @GetMapping("/{id}/history")
    public Result<?> history(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(deviceService.history(id, start, end, limit));
    }

    @GetMapping("/{id}/trend")
    public Result<?> trend(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "60") int bucketMinutes) {
        return Result.ok(deviceService.trend(id, start, end, bucketMinutes));
    }

    @PutMapping("/{id}/threshold")
    public Result<?> threshold(@PathVariable Long id, @Valid @RequestBody ThresholdRequest request) {
        return Result.ok(deviceService.updateThreshold(id, request.threshold()));
    }
}
