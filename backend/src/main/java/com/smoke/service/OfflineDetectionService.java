package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.entity.Device;
import com.smoke.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OfflineDetectionService {

    private final DeviceMapper deviceMapper;
    private final AlertService alertService;
    private final DeviceOnlinePolicy deviceOnlinePolicy;

    @Scheduled(fixedDelayString = "${smoke.offline-check-interval-ms:3000}")
    @Transactional
    public void detectOfflineDevices() {
        LocalDateTime cutoff = deviceOnlinePolicy.offlineCutoff(LocalDateTime.now());
        List<Device> timedOutDevices = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getBound, 1)
                .eq(Device::getStatus, 1)
                .and(query -> query.isNull(Device::getLastHeartbeat)
                        .or()
                        .lt(Device::getLastHeartbeat, cutoff)));

        for (Device device : timedOutDevices) {
            int updated = deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                    .set(Device::getStatus, 0)
                    .eq(Device::getId, device.getId())
                    .eq(Device::getBound, 1)
                    .eq(Device::getStatus, 1)
                    .and(query -> query.isNull(Device::getLastHeartbeat)
                            .or()
                            .lt(Device::getLastHeartbeat, cutoff)));
            if (updated > 0) {
                alertService.createOfflineAlertIfAbsent(device);
            }
        }
    }
}
