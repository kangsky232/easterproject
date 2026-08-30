package com.smoke.service;

import com.smoke.entity.AlertRecord;
import com.smoke.entity.Device;
import com.smoke.entity.DeviceMapPosition;
import com.smoke.entity.MapBuilding;
import com.smoke.entity.SmokeData;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.DeviceMapPositionMapper;
import com.smoke.mapper.DeviceMapper;
import com.smoke.mapper.MapBuildingMapper;
import com.smoke.mapper.SmokeDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapSceneServiceTest {

    @Mock
    private MapBuildingMapper mapBuildingMapper;
    @Mock
    private DeviceMapPositionMapper deviceMapPositionMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private SmokeDataMapper smokeDataMapper;
    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void sceneCombinesDatabasePositionLatestReadingAndActiveAlert() {
        MapBuilding building = new MapBuilding();
        building.setBuildingCode("A1");
        building.setBuildingName("1号住宅楼");
        building.setPositionX(new BigDecimal("16"));
        building.setPositionZ(new BigDecimal("18"));
        building.setWidth(new BigDecimal("18"));
        building.setDepth(new BigDecimal("14"));
        building.setFloors(6);

        Device device = new Device();
        device.setId(9L);
        device.setDeviceId("sensor-9");
        device.setDeviceName("9号烟感");
        device.setLocation("1栋-301");
        device.setStatus(1);
        device.setBound(1);
        device.setBattery(88);
        device.setLastHeartbeat(LocalDateTime.now().minusMinutes(2));

        DeviceMapPosition position = new DeviceMapPosition();
        position.setDeviceId("sensor-9");
        position.setBuildingCode("A1");
        position.setFloorNo(3);
        position.setRoomLabel("301");
        position.setPositionX(new BigDecimal("5"));
        position.setPositionZ(new BigDecimal("6"));

        SmokeData reading = new SmokeData();
        reading.setDeviceId("sensor-9");
        reading.setConcentration(new BigDecimal("320.50"));
        reading.setTemperature(new BigDecimal("48.20"));
        reading.setTimestamp(LocalDateTime.of(2026, 8, 28, 10, 0));

        AlertRecord alert = new AlertRecord();
        alert.setDeviceId("sensor-9");
        alert.setAlertType(AlertRecord.TYPE_SMOKE);
        alert.setSeverity(AlertRecord.SEVERITY_DANGER);
        alert.setStatus(AlertRecord.STATUS_PENDING);

        when(mapBuildingMapper.selectList(any())).thenReturn(List.of(building));
        when(deviceMapper.selectList(any())).thenReturn(List.of(device));
        when(deviceMapPositionMapper.selectList(any())).thenReturn(List.of(position));
        when(smokeDataMapper.selectLatestByDeviceIds(anyList())).thenReturn(List.of(reading));
        when(alertRecordMapper.selectList(any())).thenReturn(List.of(alert));
        MapSceneService service = new MapSceneService(
                mapBuildingMapper, deviceMapPositionMapper, deviceMapper,
                smokeDataMapper, alertRecordMapper, jdbcTemplate, new DeviceOnlinePolicy(60L));

        var scene = service.scene();

        assertEquals("康居智慧社区", scene.sceneName());
        assertEquals(1, scene.buildings().size());
        assertEquals(1, scene.devices().size());
        assertEquals("ALARM", scene.devices().get(0).status());
        assertEquals("DANGER", scene.devices().get(0).alertSeverity());
        assertEquals(new BigDecimal("320.50"), scene.devices().get(0).smoke());
        assertEquals("301", scene.devices().get(0).roomLabel());
        verify(jdbcTemplate).update(any(String.class));
        assertFalse(scene.devices().get(0).online());
    }

    @Test
    void sceneMarksStaleStatusFlagOfflineWhenThereIsNoSensorAlarm() {
        Device device = new Device();
        device.setId(10L);
        device.setDeviceId("sensor-10");
        device.setStatus(1);
        device.setBound(1);
        device.setLastHeartbeat(LocalDateTime.now().minusMinutes(2));

        when(mapBuildingMapper.selectList(any())).thenReturn(List.of());
        when(deviceMapper.selectList(any())).thenReturn(List.of(device));
        when(deviceMapPositionMapper.selectList(any())).thenReturn(List.of());
        when(smokeDataMapper.selectLatestByDeviceIds(anyList())).thenReturn(List.of());
        when(alertRecordMapper.selectList(any())).thenReturn(List.of());
        MapSceneService service = new MapSceneService(
                mapBuildingMapper, deviceMapPositionMapper, deviceMapper,
                smokeDataMapper, alertRecordMapper, jdbcTemplate, new DeviceOnlinePolicy(60L));

        var scene = service.scene();

        assertEquals("OFFLINE", scene.devices().get(0).status());
        assertFalse(scene.devices().get(0).online());
    }
}
