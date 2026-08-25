package com.smoke.service;

import com.smoke.entity.Device;
import com.smoke.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlertService alertService;

    @Test
    void heartbeatMarksDeviceOnlineAndResolvesOfflineAlert() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceId("SMOKE-001");
        device.setStatus(0);
        when(deviceMapper.selectOne(any())).thenReturn(device);
        HeartbeatService service = new HeartbeatService(deviceMapper, alertService);

        Device result = service.heartbeat("SMOKE-001", 86);

        assertEquals(1, result.getStatus());
        assertEquals(86, result.getBattery());
        assertNotNull(result.getLastHeartbeat());
        verify(deviceMapper).updateById(device);
        verify(alertService).resolveOfflineAlerts("SMOKE-001");
    }
}
