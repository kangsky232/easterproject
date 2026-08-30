package com.smoke.service;

import com.smoke.entity.Device;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Single source of truth for deciding whether a bound device is online.
 *
 * <p>The persisted status flag is deliberately not enough: it can stay at 1
 * between scheduler runs or after an unclean shutdown. A device is online only
 * while both the status flag and a recent heartbeat/telemetry timestamp agree.</p>
 */
@Component
public class DeviceOnlinePolicy {

    private final long offlineTimeoutSeconds;

    public DeviceOnlinePolicy(@Value("${smoke.offline-timeout-seconds:60}") long offlineTimeoutSeconds) {
        if (offlineTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("smoke.offline-timeout-seconds must be greater than 0");
        }
        this.offlineTimeoutSeconds = offlineTimeoutSeconds;
    }

    public boolean isOnline(Device device, LocalDateTime referenceTime) {
        LocalDateTime lastHeartbeat = device.getLastHeartbeat();
        return Integer.valueOf(1).equals(device.getBound())
                && Integer.valueOf(1).equals(device.getStatus())
                && lastHeartbeat != null
                && !lastHeartbeat.isBefore(offlineCutoff(referenceTime));
    }

    public LocalDateTime offlineCutoff(LocalDateTime referenceTime) {
        return referenceTime.minusSeconds(offlineTimeoutSeconds);
    }
}
