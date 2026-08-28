package com.smoke.dto;

import java.util.List;

public record MapSceneResponse(
        String sceneCode,
        String sceneName,
        int width,
        int depth,
        List<MapBuildingResponse> buildings,
        List<MapDeviceResponse> devices) {
}
