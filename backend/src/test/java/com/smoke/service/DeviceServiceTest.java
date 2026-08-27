package com.smoke.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.dto.BindDeviceRequest;
import com.smoke.dto.CurrentReadingResponse;
import com.smoke.dto.DeviceSummaryResponse;
import com.smoke.dto.PageResponse;
import com.smoke.dto.TrendPointResponse;
import com.smoke.entity.Device;
import com.smoke.entity.SmokeData;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.DeviceMapper;
import com.smoke.mapper.SmokeDataMapper;
import com.smoke.security.DeviceCredentialCodec;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "device-test"), Device.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "smoke-test"), SmokeData.class);
    }

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private SmokeDataMapper smokeDataMapper;

    @Mock
    private AlertService alertService;

    @Test
    void bindCreatesOfflineDeviceWithDefaultThreshold() {
        when(deviceMapper.selectOne(any())).thenReturn(null);
        DeviceService service = new DeviceService(deviceMapper, smokeDataMapper, alertService);

        service.bind(new BindDeviceRequest("SMOKE-001", "1号烟感", "1栋101室"));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(captor.capture());
        assertEquals("SMOKE-001", captor.getValue().getDeviceId());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getBound());
        assertEquals(100, captor.getValue().getSmokeThreshold());
        assertNotNull(captor.getValue().getDeviceAccessToken());
        assertTrue(DeviceCredentialCodec.matches(
                captor.getValue().getDeviceAccessToken(), captor.getValue().getDeviceTokenHash()));
    }

    @Test
    void updateThresholdRejectsValuesOutsideFixedSafetyRule() {
        DeviceService service = new DeviceService(deviceMapper, smokeDataMapper, alertService);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.updateThreshold(1L, 300));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("100 ppm"));
    }

    @Test
    void currentReturnsLatestReading() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceId("SMOKE-001");
        device.setDeviceName("1号烟感");
        device.setSmokeThreshold(2000);
        device.setStatus(0);
        device.setBound(1);
        SmokeData reading = new SmokeData();
        reading.setConcentration(new BigDecimal("380.25"));
        reading.setTemperature(new BigDecimal("27.43"));
        reading.setHumidity(new BigDecimal("59.42"));
        reading.setCurrentValue(new BigDecimal("2.01"));
        reading.setWireTemperature(new BigDecimal("28.18"));
        reading.setCoValue(new BigDecimal("0.93"));
        reading.setBeepStatus("OFF");
        reading.setTimestamp(LocalDateTime.of(2026, 8, 22, 10, 0));
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(smokeDataMapper.selectOne(any())).thenReturn(reading);
        DeviceService service = new DeviceService(deviceMapper, smokeDataMapper, alertService);

        CurrentReadingResponse response = service.current(1L);

        assertEquals(new BigDecimal("380.25"), response.concentration());
        assertEquals(new BigDecimal("27.43"), response.temperature());
        assertEquals(new BigDecimal("59.42"), response.humidity());
        assertEquals(new BigDecimal("2.01"), response.current());
        assertEquals(new BigDecimal("28.18"), response.wireTemperature());
        assertEquals(new BigDecimal("0.93"), response.coValue());
        assertEquals("OFF", response.beepStatus());
        assertFalse(response.online());
    }

    @Test
    void historyRejectsReversedTimeRange() {
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        DeviceService service = new DeviceService(deviceMapper, smokeDataMapper, alertService);

        LocalDateTime start = LocalDateTime.of(2026, 8, 23, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 22, 0, 0);

        assertThrows(BusinessException.class, () -> service.history(1L, start, end, 100));
    }

    @Test
    void listAddsLatestReadingWithoutOneQueryPerDevice() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceId("SMOKE-001");
        device.setDeviceName("1号烟感");
        device.setBound(1);
        device.setStatus(1);
        Page<Device> devices = new Page<>(1, 20);
        devices.setRecords(List.of(device));
        devices.setTotal(1);
        SmokeData reading = new SmokeData();
        reading.setDeviceId("SMOKE-001");
        reading.setConcentration(new BigDecimal("360.75"));
        reading.setTemperature(new BigDecimal("26.80"));
        reading.setBeepStatus("ON");
        reading.setTimestamp(LocalDateTime.of(2026, 8, 22, 10, 5));
        when(deviceMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<Device>>any(),
                org.mockito.ArgumentMatchers.<Wrapper<Device>>any())).thenReturn(devices);
        when(smokeDataMapper.selectLatestByDeviceIds(anyList())).thenReturn(List.of(reading));
        DeviceService service = new DeviceService(deviceMapper, smokeDataMapper, alertService);

        PageResponse<DeviceSummaryResponse> result = service.list(null, null, 1, 20);

        assertEquals(1, result.total());
        assertEquals(new BigDecimal("360.75"), result.records().get(0).latestConcentration());
        assertEquals(new BigDecimal("26.80"), result.records().get(0).latestTemperature());
        assertEquals("ON", result.records().get(0).latestBeepStatus());
        verify(smokeDataMapper).selectLatestByDeviceIds(List.of("SMOKE-001"));
    }

    @Test
    void trendAggregatesReadingsIntoTimeBuckets() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        SmokeData first = reading("100.10", LocalDateTime.of(2026, 8, 22, 10, 5));
        SmokeData second = reading("300.20", LocalDateTime.of(2026, 8, 22, 10, 20));
        SmokeData third = reading("500.30", LocalDateTime.of(2026, 8, 22, 10, 50));
        when(smokeDataMapper.selectList(any())).thenReturn(List.of(first, second, third));
        DeviceService service = new DeviceService(deviceMapper, smokeDataMapper, alertService);

        List<TrendPointResponse> result = service.trend(
                1L,
                LocalDateTime.of(2026, 8, 22, 10, 0),
                LocalDateTime.of(2026, 8, 22, 11, 0),
                60);

        assertEquals(1, result.size());
        assertEquals("300.20", result.get(0).average().toPlainString());
        assertEquals(new BigDecimal("100.10"), result.get(0).minimum());
        assertEquals(new BigDecimal("500.30"), result.get(0).maximum());
        assertEquals(3, result.get(0).samples());
    }

    private SmokeData reading(String concentration, LocalDateTime timestamp) {
        SmokeData reading = new SmokeData();
        reading.setConcentration(new BigDecimal(concentration));
        reading.setTimestamp(timestamp);
        return reading;
    }
}
