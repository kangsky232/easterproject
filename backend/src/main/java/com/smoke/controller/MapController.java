package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.dto.UpdateMapPositionRequest;
import com.smoke.service.MapSceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapController {

    private final MapSceneService mapSceneService;

    @GetMapping("/scene")
    public Result<?> scene() {
        return Result.ok(mapSceneService.scene());
    }

    @PutMapping("/devices/{id}/position")
    public Result<?> updatePosition(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMapPositionRequest request) {
        return Result.ok(mapSceneService.updatePosition(id, request));
    }
}
