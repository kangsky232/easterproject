package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.entity.Device;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final DeviceMapper deviceMapper;
    private final AlertService alertService;

    @Transactional
    public Device heartbeat(String deviceId, Integer battery) {
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, deviceId)
                .eq(Device::getBound, 1));
        if (device == null) {
            throw new BusinessException(404, "设备不存在或尚未绑定");
        }
        device.setStatus(1);
        device.setLastHeartbeat(LocalDateTime.now());
        if (battery != null) {
            device.setBattery(battery);
        }
        deviceMapper.updateById(device);
        alertService.resolveOfflineAlerts(deviceId);
        return device;
    }
}
