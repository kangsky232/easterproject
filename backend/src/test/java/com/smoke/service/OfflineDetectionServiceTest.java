package com.smoke.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smoke.entity.Device;
import com.smoke.mapper.DeviceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineDetectionServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Device.class);
    }

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlertService alertService;

    @Test
    void heartbeatRaceDoesNotCreateOfflineAlertWhenConditionalUpdateMisses() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceId("SMOKE-001");
        when(deviceMapper.selectList(any())).thenReturn(List.of(device));
        when(deviceMapper.update(isNull(), any())).thenReturn(0);
        OfflineDetectionService service = new OfflineDetectionService(deviceMapper, alertService);
        ReflectionTestUtils.setField(service, "offlineTimeoutSeconds", 60L);

        service.detectOfflineDevices();

        verify(alertService, never()).createOfflineAlertIfAbsent(any());
    }
}
