package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.BroadcastStatusRequest;
import com.smoke.dto.CreateBroadcastRequest;
import com.smoke.service.BroadcastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broadcasts")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;

    @GetMapping
    public Result<?> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(broadcastService.list(deviceId, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(broadcastService.get(id));
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody CreateBroadcastRequest request) {
        return Result.ok(broadcastService.create(request));
    }

    @PostMapping("/{id}/deliver")
    public Result<?> deliver(@PathVariable Long id) {
        return Result.ok(broadcastService.deliver(id));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        return Result.ok(broadcastService.delete(id));
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BroadcastStatusRequest request) {
        return Result.ok(broadcastService.updateStatus(id, request.status()));
    }
}
