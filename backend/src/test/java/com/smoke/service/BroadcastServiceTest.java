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

    @Test
    void createStoresPendingBroadcastForBoundDevice() {
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        when(deviceMapper.selectOne(any())).thenReturn(device);
        BroadcastService service = new BroadcastService(broadcastLogMapper, deviceMapper, alertRecordMapper);

        service.create(new CreateBroadcastRequest("SMOKE-001", "请立即疏散", null));

        ArgumentCaptor<BroadcastLog> captor = ArgumentCaptor.forClass(BroadcastLog.class);
        verify(broadcastLogMapper).insert(captor.capture());
        assertEquals(BroadcastLog.STATUS_PENDING, captor.getValue().getStatus());
        assertEquals("请立即疏散", captor.getValue().getContent());
    }
}
