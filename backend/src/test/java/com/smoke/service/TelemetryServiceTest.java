package com.smoke.service;

import com.smoke.dto.TelemetryRequest;
import com.smoke.dto.TelemetryResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.Device;
import com.smoke.mapper.DeviceMapper;
import com.smoke.mapper.SmokeDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private SmokeDataMapper smokeDataMapper;

    @Mock
    private AlertService alertService;

    @Test
    void recordPersistsReadingAndMarksDeviceOnline() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceId("SMOKE-001");
        device.setSmokeThreshold(2000);
        when(deviceMapper.selectOne(any())).thenReturn(device);
        AlertRecord alert = new AlertRecord();
        alert.setId(10L);
        BigDecimal concentration = new BigDecimal("2500.25");
        when(alertService.createSmokeAlertIfAbsent(device, concentration, 2000)).thenReturn(alert);
        TelemetryService service = new TelemetryService(deviceMapper, smokeDataMapper, alertService);

        TelemetryResponse response = service.record(
                new TelemetryRequest("SMOKE-001", concentration, "msg-001", null));

        assertTrue(response.accepted());
        assertFalse(response.duplicate());
        assertTrue(response.thresholdExceeded());
        assertEquals(10L, response.alert().getId());
        assertEquals(1, device.getStatus());
        assertEquals(new BigDecimal("2500.25"), response.record().getConcentration());
        verify(smokeDataMapper).insert(response.record());
        verify(deviceMapper).updateById(device);
        verify(alertService).resolveOfflineAlerts("SMOKE-001");
    }
}
