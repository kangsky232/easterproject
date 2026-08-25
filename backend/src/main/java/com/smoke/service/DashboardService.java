package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.dto.DeviceOverviewResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.Device;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DeviceMapper deviceMapper;
    private final AlertRecordMapper alertRecordMapper;

    public DeviceOverviewResponse overview() {
        long total = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getBound, 1));
        long online = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getBound, 1)
                .eq(Device::getStatus, 1));
        long activeAlerts = alertRecordMapper.selectCount(Wrappers.<AlertRecord>lambdaQuery()
                .in(AlertRecord::getStatus, AlertRecord.STATUS_PENDING, AlertRecord.STATUS_CONFIRMED));
        return new DeviceOverviewResponse(total, online, total - online, activeAlerts);
    }
}
