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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogMapper notificationLogMapper;
    @Mock
    private DingTalkMessageService dingTalkMessageService;

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
        assertEquals(NotificationLog.AUDIT_PENDING, captor.getAllValues().get(0).getAuditStatus());
        assertEquals(NotificationLog.AUDIT_PENDING, captor.getAllValues().get(1).getAuditStatus());
    }

    @Test
    void summaryProvidesChannelAndDeliveryStatusCounts() {
        when(notificationLogMapper.selectCount(any())).thenReturn(15L, 6L, 6L, 3L, 1L, 13L, 1L, 4L, 11L, 1L);
        NotificationService service = new NotificationService(notificationLogMapper);

        var summary = service.summary();

        assertEquals(15L, summary.total());
        assertEquals(6L, summary.appCount());
        assertEquals(6L, summary.smsCount());
        assertEquals(3L, summary.dingTalkCount());
        assertEquals(1L, summary.pendingCount());
        assertEquals(13L, summary.sentCount());
        assertEquals(1L, summary.failedCount());
        assertEquals(4L, summary.pendingAuditCount());
        assertEquals(11L, summary.completedAuditCount());
        assertEquals(1L, summary.attentionCount());
    }

    @Test
    void createForAlertDeliversConfiguredDingTalkNotification() {
        AlertRecord alert = new AlertRecord();
        alert.setId(8L);
        alert.setDeviceId("SMOKE-001");
        alert.setAlertType(AlertRecord.TYPE_CO);
        alert.setConcentration(new java.math.BigDecimal("120"));
        alert.setSeverity(AlertRecord.SEVERITY_DANGER);
        alert.setRuleDescription("一氧化碳浓度 > 100 ppm");
        when(dingTalkMessageService.isConfigured()).thenReturn(true);
        when(dingTalkMessageService.sendAlert(any(), any())).thenReturn(1);
        NotificationService service = new NotificationService(notificationLogMapper, dingTalkMessageService);

        service.createForAlert(alert);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogMapper, times(3)).insert(captor.capture());
        NotificationLog dingTalk = captor.getAllValues().get(2);
        assertEquals(NotificationLog.CHANNEL_DINGTALK, dingTalk.getChannel());
        assertEquals(NotificationLog.STATUS_SENT, dingTalk.getStatus());
        assertTrue(dingTalk.getContent().contains("一氧化碳浓度 120ppm"));
    }

    @Test
    void createForAlertRecordsFailedDingTalkDeliveryWithoutRollingBackAlert() {
        AlertRecord alert = new AlertRecord();
        alert.setId(9L);
        alert.setDeviceId("SMOKE-001");
        alert.setAlertType(AlertRecord.TYPE_TEMPERATURE);
        alert.setConcentration(new java.math.BigDecimal("61"));
        alert.setSeverity(AlertRecord.SEVERITY_DANGER);
        alert.setRuleDescription("环境温度 > 60℃");
        when(dingTalkMessageService.isConfigured()).thenReturn(true);
        doThrow(new DingTalkMessageService.DingTalkDeliveryException("network error"))
                .when(dingTalkMessageService).sendAlert(any(), any());
        NotificationService service = new NotificationService(notificationLogMapper, dingTalkMessageService);

        service.createForAlert(alert);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogMapper, times(3)).insert(captor.capture());
        NotificationLog dingTalk = captor.getAllValues().get(2);
        assertEquals(NotificationLog.CHANNEL_DINGTALK, dingTalk.getChannel());
        assertEquals(NotificationLog.STATUS_FAILED, dingTalk.getStatus());
        assertNull(dingTalk.getSentAt());
    }

    @Test
    void getRejectsUnknownNotification() {
        when(notificationLogMapper.selectById(99L)).thenReturn(null);
        NotificationService service = new NotificationService(notificationLogMapper);

        assertThrows(com.smoke.exception.BusinessException.class, () -> service.get(99L));
    }

    @Test
    void auditRecordsImmutableOperatorConclusion() {
        NotificationLog notification = new NotificationLog();
        notification.setId(12L);
        notification.setAuditStatus(NotificationLog.AUDIT_PENDING);
        when(notificationLogMapper.selectById(12L)).thenReturn(notification);
        when(notificationLogMapper.update(any(), any())).thenReturn(1);
        NotificationService service = new NotificationService(notificationLogMapper);

        var response = service.audit(12L, "followed_up", " 已联系值班人员并核对接收配置 ", "firefighter");

        assertEquals(NotificationLog.AUDIT_COMPLETED, response.auditStatus());
        assertEquals(NotificationLog.AUDIT_RESULT_FOLLOWED_UP, response.auditResult());
        assertEquals("firefighter", response.auditorUsername());
        assertEquals("已联系值班人员并核对接收配置", response.auditRemark());
        assertNotNull(response.auditedAt());
        verify(notificationLogMapper).update(any(), any());
    }

    @Test
    void auditRejectsRepeatedModification() {
        NotificationLog notification = new NotificationLog();
        notification.setId(13L);
        notification.setAuditStatus(NotificationLog.AUDIT_COMPLETED);
        when(notificationLogMapper.selectById(13L)).thenReturn(notification);
        NotificationService service = new NotificationService(notificationLogMapper);

        assertThrows(
                com.smoke.exception.BusinessException.class,
                () -> service.audit(13L, "NORMAL", "重复修改", "admin"));
    }

    @Test
    void listRejectsUnsupportedDeliveryFilters() {
        NotificationService service = new NotificationService(notificationLogMapper);

        assertThrows(
                com.smoke.exception.BusinessException.class,
                () -> service.list(1, 20, null, null, "EMAIL", null, null));
        assertThrows(
                com.smoke.exception.BusinessException.class,
                () -> service.list(1, 20, null, null, null, "UNKNOWN", null));
        assertThrows(
                com.smoke.exception.BusinessException.class,
                () -> service.list(1, 20, null, null, null, null, "UNKNOWN"));
    }
}
