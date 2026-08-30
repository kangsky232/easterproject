package com.smoke.service;

import com.smoke.entity.Device;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceOnlinePolicyTest {

    private final DeviceOnlinePolicy policy = new DeviceOnlinePolicy(60L);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 30, 22, 0);

    @Test
    void onlineRequiresBoundEnabledStatusAndRecentHeartbeat() {
        Device device = device(1, 1, now.minusSeconds(59));

        assertTrue(policy.isOnline(device, now));
        assertFalse(policy.isOnline(device(1, 1, now.minusSeconds(61)), now));
        assertFalse(policy.isOnline(device(1, 1, null), now));
        assertFalse(policy.isOnline(device(1, 0, now), now));
        assertFalse(policy.isOnline(device(0, 1, now), now));
    }

    @Test
    void heartbeatAtTimeoutBoundaryIsStillOnline() {
        assertTrue(policy.isOnline(device(1, 1, now.minusSeconds(60)), now));
    }

    private Device device(int bound, int status, LocalDateTime lastHeartbeat) {
        Device device = new Device();
        device.setBound(bound);
        device.setStatus(status);
        device.setLastHeartbeat(lastHeartbeat);
        return device;
    }
}
