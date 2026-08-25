package com.smoke.service;

import com.smoke.entity.AlertRecord;
import com.smoke.entity.NotificationLog;
import com.smoke.mapper.NotificationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogMapper notificationLogMapper;

    @Test
    void createForAlertStoresAppAndSmsNotifications() {
        AlertRecord alert = new AlertRecord();
        alert.setId(7L);
        alert.setDeviceId("SMOKE-001");
        alert.setAlertType(AlertRecord.TYPE_SMOKE);
        NotificationService service = new NotificationService(notificationLogMapper);

        service.createForAlert(alert);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogMapper, times(2)).insert(captor.capture());
        assertEquals(NotificationLog.CHANNEL_APP, captor.getAllValues().get(0).getChannel());
        assertEquals(NotificationLog.CHANNEL_SMS, captor.getAllValues().get(1).getChannel());
        assertEquals(7L, captor.getAllValues().get(0).getAlertId());
        assertEquals(NotificationLog.STATUS_SENT, captor.getAllValues().get(0).getStatus());
        assertNotNull(captor.getAllValues().get(0).getSentAt());
        assertEquals(NotificationLog.STATUS_PENDING, captor.getAllValues().get(1).getStatus());
        assertNull(captor.getAllValues().get(1).getSentAt());
    }

    @Test
    void summaryProvidesChannelAndDeliveryStatusCounts() {
        when(notificationLogMapper.selectCount(any())).thenReturn(12L, 6L, 6L, 1L, 10L, 1L);
        NotificationService service = new NotificationService(notificationLogMapper);

        var summary = service.summary();

        assertEquals(12L, summary.total());
        assertEquals(6L, summary.appCount());
        assertEquals(6L, summary.smsCount());
        assertEquals(1L, summary.pendingCount());
        assertEquals(10L, summary.sentCount());
        assertEquals(1L, summary.failedCount());
    }

    @Test
    void getRejectsUnknownNotification() {
        when(notificationLogMapper.selectById(99L)).thenReturn(null);
        NotificationService service = new NotificationService(notificationLogMapper);

        assertThrows(com.smoke.exception.BusinessException.class, () -> service.get(99L));
    }

    @Test
    void listRejectsUnsupportedDeliveryFilters() {
        NotificationService service = new NotificationService(notificationLogMapper);

        assertThrows(
                com.smoke.exception.BusinessException.class,
                () -> service.list(1, 20, null, null, "EMAIL", null));
        assertThrows(
                com.smoke.exception.BusinessException.class,
                () -> service.list(1, 20, null, null, null, "UNKNOWN"));
    }
}
