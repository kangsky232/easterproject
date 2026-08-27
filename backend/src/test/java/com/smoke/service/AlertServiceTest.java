package com.smoke.service;

import com.smoke.entity.AlertRecord;
import com.smoke.entity.Device;
import com.smoke.mapper.AlertRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Mock
    private NotificationService notificationService;

    @Test
    void createSmokeAlertStoresThresholdContext() {
        when(alertRecordMapper.selectOne(any())).thenReturn(null);
        AlertService service = new AlertService(alertRecordMapper);
        Device device = new Device();
        device.setDeviceId("SMOKE-001");

        service.createSmokeAlertIfAbsent(device, new BigDecimal("150.25"), 100);

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        assertEquals(AlertRecord.TYPE_SMOKE, captor.getValue().getAlertType());
        assertEquals(new BigDecimal("150.25"), captor.getValue().getConcentration());
        assertEquals(100, captor.getValue().getThreshold());
        assertEquals(AlertRecord.SEVERITY_WARNING, captor.getValue().getSeverity());
        assertEquals(AlertRecord.STATUS_PENDING, captor.getValue().getStatus());
    }

    @Test
    void createSmokeAlertReusesExistingActiveAlert() {
        AlertRecord existing = new AlertRecord();
        existing.setId(10L);
        when(alertRecordMapper.selectOne(any())).thenReturn(existing);
        AlertService service = new AlertService(alertRecordMapper);
        Device device = new Device();
        device.setDeviceId("SMOKE-001");

        AlertRecord result = service.createSmokeAlertIfAbsent(device, new BigDecimal("150.25"), 100);

        assertSame(existing, result);
        verify(alertRecordMapper, never()).insert(any(AlertRecord.class));
    }

    @Test
    void activeWarningIsUpdatedAndNotifiedWhenItEscalatesToDanger() {
        AlertRecord existing = new AlertRecord();
        existing.setId(10L);
        existing.setSeverity(AlertRecord.SEVERITY_WARNING);
        when(alertRecordMapper.selectOne(any())).thenReturn(existing);
        AlertService service = new AlertService(alertRecordMapper, notificationService);
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        var signal = new TelemetryAlertEvaluator.AlertSignal(
                AlertRecord.TYPE_SMOKE,
                AlertRecord.SEVERITY_DANGER,
                new BigDecimal("350"),
                300,
                "烟雾浓度 > 300 ppm");

        service.createSensorAlerts(device, java.util.List.of(signal));

        assertEquals(AlertRecord.SEVERITY_DANGER, existing.getSeverity());
        assertEquals(new BigDecimal("350"), existing.getConcentration());
        verify(alertRecordMapper).updateById(existing);
        verify(notificationService).createForAlert(existing);
    }

    @Test
    void confirmRecordsOperatorAndStatus() {
        AlertRecord alert = new AlertRecord();
        alert.setId(10L);
        alert.setStatus(AlertRecord.STATUS_PENDING);
        when(alertRecordMapper.selectById(10L)).thenReturn(alert);
        AlertService service = new AlertService(alertRecordMapper);

        service.confirm(10L, "security-user");

        assertEquals(AlertRecord.STATUS_CONFIRMED, alert.getStatus());
        assertEquals("security-user", alert.getConfirmedBy());
        verify(alertRecordMapper).updateById(alert);
    }

    @Test
    void markFalseAlarmResolvesAndFlagsAlert() {
        AlertRecord alert = new AlertRecord();
        alert.setId(10L);
        alert.setStatus(AlertRecord.STATUS_PENDING);
        alert.setFalseAlarm(0);
        when(alertRecordMapper.selectById(10L)).thenReturn(alert);
        AlertService service = new AlertService(alertRecordMapper);

        service.markFalseAlarm(10L, "security-user");

        assertEquals(AlertRecord.STATUS_RESOLVED, alert.getStatus());
        assertEquals(1, alert.getFalseAlarm());
        assertEquals("security-user", alert.getConfirmedBy());
        verify(alertRecordMapper).updateById(alert);
    }
}
