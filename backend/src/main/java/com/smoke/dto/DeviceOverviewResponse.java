package com.smoke.dto;

public record DeviceOverviewResponse(
        long totalDevices,
        long onlineDevices,
        long offlineDevices,
        long activeAlerts) {
}
