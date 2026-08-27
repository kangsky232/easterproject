package com.smoke.service;

import com.smoke.dto.CreateBroadcastRequest;
import com.smoke.entity.BroadcastLog;
import com.smoke.entity.Device;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.BroadcastLogMapper;
import com.smoke.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock
    private BroadcastLogMapper broadcastLogMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Mock
    private DingTalkMessageService dingTalkMessageService;

    @Test
    void createStoresPendingBroadcastForBoundDevice() {
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        when(deviceMapper.selectOne(any())).thenReturn(device);
        BroadcastService service = new BroadcastService(
                broadcastLogMapper, deviceMapper, alertRecordMapper, dingTalkMessageService);

        service.create(new CreateBroadcastRequest("SMOKE-001", "请立即疏散", null));

        ArgumentCaptor<BroadcastLog> captor = ArgumentCaptor.forClass(BroadcastLog.class);
        verify(broadcastLogMapper).insert(captor.capture());
        assertEquals(BroadcastLog.STATUS_PENDING, captor.getValue().getStatus());
        assertEquals("请立即疏散", captor.getValue().getContent());
    }

    @Test
    void createMarksBroadcastSuccessfulAfterDingTalkDelivery() {
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        when(deviceMapper.selectOne(any())).thenReturn(device);
        when(dingTalkMessageService.isConfigured()).thenReturn(true);
        BroadcastService service = new BroadcastService(
                broadcastLogMapper, deviceMapper, alertRecordMapper, dingTalkMessageService);

        BroadcastLog result = service.create(
                new CreateBroadcastRequest("SMOKE-001", "Evacuate immediately", null));

        verify(dingTalkMessageService).sendBroadcast("SMOKE-001", "Evacuate immediately");
        verify(broadcastLogMapper).updateById(result);
        assertEquals(BroadcastLog.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void createMarksBroadcastFailedWhenDingTalkRejectsDelivery() {
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        when(deviceMapper.selectOne(any())).thenReturn(device);
        when(dingTalkMessageService.isConfigured()).thenReturn(true);
        doThrow(new DingTalkMessageService.DingTalkDeliveryException("rejected"))
                .when(dingTalkMessageService)
                .sendBroadcast("SMOKE-001", "Evacuate immediately");
        BroadcastService service = new BroadcastService(
                broadcastLogMapper, deviceMapper, alertRecordMapper, dingTalkMessageService);

        BroadcastLog result = service.create(
                new CreateBroadcastRequest("SMOKE-001", "Evacuate immediately", null));

        verify(broadcastLogMapper).updateById(result);
        assertEquals(BroadcastLog.STATUS_FAILED, result.getStatus());
    }

    @Test
    void deliverResendsExistingBroadcastAndUpdatesStatus() {
        BroadcastLog broadcast = existingBroadcast();
        when(broadcastLogMapper.selectById(12L)).thenReturn(broadcast);
        when(dingTalkMessageService.isConfigured()).thenReturn(true);
        BroadcastService service = new BroadcastService(
                broadcastLogMapper, deviceMapper, alertRecordMapper, dingTalkMessageService);

        BroadcastLog result = service.deliver(12L);

        verify(dingTalkMessageService).sendBroadcast("SMOKE-001", "Evacuate immediately");
        verify(broadcastLogMapper).updateById(broadcast);
        assertEquals(BroadcastLog.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void deleteRemovesExistingBroadcastRecord() {
        BroadcastLog broadcast = existingBroadcast();
        when(broadcastLogMapper.selectById(12L)).thenReturn(broadcast);
        BroadcastService service = new BroadcastService(
                broadcastLogMapper, deviceMapper, alertRecordMapper, dingTalkMessageService);

        BroadcastLog result = service.delete(12L);

        verify(broadcastLogMapper).deleteById(12L);
        assertEquals(12L, result.getId());
    }

    private BroadcastLog existingBroadcast() {
        BroadcastLog broadcast = new BroadcastLog();
        broadcast.setId(12L);
        broadcast.setDeviceId("SMOKE-001");
        broadcast.setContent("Evacuate immediately");
        broadcast.setStatus(BroadcastLog.STATUS_FAILED);
        return broadcast;
    }
}
