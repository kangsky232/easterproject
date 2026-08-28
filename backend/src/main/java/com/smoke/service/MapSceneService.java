package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.dto.MapBuildingResponse;
import com.smoke.dto.MapDeviceResponse;
import com.smoke.dto.MapPositionResponse;
import com.smoke.dto.MapSceneResponse;
import com.smoke.dto.UpdateMapPositionRequest;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.Device;
import com.smoke.entity.DeviceMapPosition;
import com.smoke.entity.MapBuilding;
import com.smoke.entity.SmokeData;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.DeviceMapPositionMapper;
import com.smoke.mapper.DeviceMapper;
import com.smoke.mapper.MapBuildingMapper;
import com.smoke.mapper.SmokeDataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapSceneService {

    private final MapBuildingMapper mapBuildingMapper;
    private final DeviceMapPositionMapper deviceMapPositionMapper;
    private final DeviceMapper deviceMapper;
    private final SmokeDataMapper smokeDataMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public MapSceneResponse scene() {
        assignDefaultPositions();
        List<MapBuilding> buildings = mapBuildingMapper.selectList(Wrappers.<MapBuilding>lambdaQuery()
                .eq(MapBuilding::getEnabled, 1)
                .orderByAsc(MapBuilding::getBuildingCode));
        List<Device> devices = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getBound, 1)
                .orderByAsc(Device::getId));

        Map<String, MapBuilding> buildingByCode = buildings.stream()
                .collect(Collectors.toMap(MapBuilding::getBuildingCode, Function.identity()));
        Map<String, DeviceMapPosition> positionByDevice = deviceMapPositionMapper.selectList(
                        Wrappers.<DeviceMapPosition>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(DeviceMapPosition::getDeviceId, Function.identity()));
        Map<String, SmokeData> latestByDevice = latestReadings(devices);
        Map<String, String> activeSeverity = activeSeverity();

        List<MapBuildingResponse> buildingResponses = buildings.stream()
                .map(building -> new MapBuildingResponse(
                        building.getBuildingCode(), building.getBuildingName(),
                        building.getPositionX(), building.getPositionZ(),
                        building.getWidth(), building.getDepth(), building.getFloors()))
                .toList();
        List<MapDeviceResponse> deviceResponses = devices.stream()
                .map(device -> toDeviceResponse(
                        device,
                        positionByDevice.get(device.getDeviceId()),
                        buildingByCode,
                        latestByDevice.get(device.getDeviceId()),
                        activeSeverity.get(device.getDeviceId())))
                .toList();
        return new MapSceneResponse("KANGROOM_DEMO", "康居智慧社区", 100, 100,
                buildingResponses, deviceResponses);
    }

    @Transactional
    public MapPositionResponse updatePosition(Long id, UpdateMapPositionRequest request) {
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getId, id)
                .eq(Device::getBound, 1));
        if (device == null) {
            throw new BusinessException(404, "设备不存在或已解绑");
        }
        String buildingCode = request.buildingCode().trim().toUpperCase();
        MapBuilding building = mapBuildingMapper.selectOne(Wrappers.<MapBuilding>lambdaQuery()
                .eq(MapBuilding::getBuildingCode, buildingCode)
                .eq(MapBuilding::getEnabled, 1));
        if (building == null) {
            throw new BusinessException(404, "地图楼栋不存在");
        }
        if (request.floorNo() > building.getFloors()) {
            throw new BusinessException(400, "楼层不能超过该楼栋总层数");
        }
        if (request.positionX().compareTo(building.getWidth()) > 0
                || request.positionZ().compareTo(building.getDepth()) > 0) {
            throw new BusinessException(400, "设备坐标必须位于楼栋范围内");
        }

        DeviceMapPosition position = new DeviceMapPosition();
        position.setDeviceId(device.getDeviceId());
        position.setBuildingCode(buildingCode);
        position.setFloorNo(request.floorNo());
        position.setRoomLabel(request.roomLabel().trim());
        position.setPositionX(request.positionX());
        position.setPositionZ(request.positionZ());
        if (deviceMapPositionMapper.selectById(device.getDeviceId()) == null) {
            deviceMapPositionMapper.insert(position);
        } else {
            deviceMapPositionMapper.updateById(position);
        }
        return new MapPositionResponse(position.getDeviceId(), position.getBuildingCode(),
                position.getFloorNo(), position.getRoomLabel(),
                position.getPositionX(), position.getPositionZ());
    }

    private void assignDefaultPositions() {
        jdbcTemplate.update("""
                INSERT IGNORE INTO device_map_position
                    (device_id, building_code, floor_no, room_label, position_x, position_z, updated_at)
                SELECT d.device_id,
                       CASE MOD(d.id - 1, 3) WHEN 0 THEN 'A1' WHEN 1 THEN 'A2' ELSE 'A3' END,
                       1 + MOD(d.id - 1, 5),
                       CONCAT(1 + MOD(d.id - 1, 5), '0', 1 + MOD(d.id - 1, 4)),
                       3 + MOD(d.id * 3, 10),
                       3 + MOD(d.id * 2, 6),
                       NOW()
                FROM device d
                WHERE d.bound = 1
                """);
    }

    private Map<String, SmokeData> latestReadings(List<Device> devices) {
        if (devices.isEmpty()) {
            return Map.of();
        }
        return smokeDataMapper.selectLatestByDeviceIds(devices.stream().map(Device::getDeviceId).toList())
                .stream()
                .collect(Collectors.toMap(SmokeData::getDeviceId, Function.identity()));
    }

    private Map<String, String> activeSeverity() {
        Map<String, String> severityByDevice = new HashMap<>();
        for (AlertRecord alert : alertRecordMapper.selectList(Wrappers.<AlertRecord>lambdaQuery()
                .in(AlertRecord::getStatus, AlertRecord.STATUS_PENDING, AlertRecord.STATUS_CONFIRMED))) {
            String severity = AlertRecord.TYPE_OFFLINE == alert.getAlertType()
                    ? "OFFLINE"
                    : alert.getSeverity() == null ? AlertRecord.SEVERITY_WARNING : alert.getSeverity();
            severityByDevice.merge(alert.getDeviceId(), severity, MapSceneService::higherSeverity);
        }
        return severityByDevice;
    }

    private static String higherSeverity(String first, String second) {
        if (AlertRecord.SEVERITY_DANGER.equals(first) || AlertRecord.SEVERITY_DANGER.equals(second)) {
            return AlertRecord.SEVERITY_DANGER;
        }
        if (AlertRecord.SEVERITY_WARNING.equals(first) || AlertRecord.SEVERITY_WARNING.equals(second)) {
            return AlertRecord.SEVERITY_WARNING;
        }
        return "OFFLINE";
    }

    private MapDeviceResponse toDeviceResponse(
            Device device,
            DeviceMapPosition position,
            Map<String, MapBuilding> buildingByCode,
            SmokeData latest,
            String alertSeverity) {
        MapBuilding building = position == null ? null : buildingByCode.get(position.getBuildingCode());
        boolean online = Integer.valueOf(1).equals(device.getStatus());
        String status = AlertRecord.SEVERITY_DANGER.equals(alertSeverity)
                || AlertRecord.SEVERITY_WARNING.equals(alertSeverity)
                ? "ALARM" : online ? "ONLINE" : "OFFLINE";
        return new MapDeviceResponse(
                device.getId(), device.getDeviceId(), device.getDeviceName(), device.getLocation(),
                position == null ? null : position.getBuildingCode(),
                building == null ? null : building.getBuildingName(),
                position == null ? null : position.getFloorNo(),
                position == null ? null : position.getRoomLabel(),
                position == null ? null : position.getPositionX(),
                position == null ? null : position.getPositionZ(),
                online, status, alertSeverity, device.getBattery(),
                latest == null ? null : latest.getConcentration(),
                latest == null ? null : latest.getTemperature(),
                latest == null ? null : latest.getHumidity(),
                latest == null ? null : latest.getCurrentValue(),
                latest == null ? null : latest.getWireTemperature(),
                latest == null ? null : latest.getCoValue(),
                latest == null ? null : latest.getTimestamp());
    }
}
