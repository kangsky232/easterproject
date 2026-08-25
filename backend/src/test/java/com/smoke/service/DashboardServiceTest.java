package com.smoke.service;

import com.smoke.dto.DeviceOverviewResponse;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Test
    void overviewAggregatesDeviceAndAlertCounts() {
        when(deviceMapper.selectCount(any())).thenReturn(3L, 1L);
        when(alertRecordMapper.selectCount(any())).thenReturn(2L);
        DashboardService service = new DashboardService(deviceMapper, alertRecordMapper);

        DeviceOverviewResponse response = service.overview();

        assertEquals(3, response.totalDevices());
        assertEquals(1, response.onlineDevices());
        assertEquals(2, response.offlineDevices());
        assertEquals(2, response.activeAlerts());
    }
}
