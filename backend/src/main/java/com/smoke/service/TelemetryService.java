package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.dto.TelemetryRequest;
import com.smoke.dto.TelemetryResponse;
import com.smoke.entity.Device;
import com.smoke.entity.SmokeData;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.DeviceMapper;
import com.smoke.mapper.SmokeDataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final DeviceMapper deviceMapper;
    private final SmokeDataMapper smokeDataMapper;
    private final AlertService alertService;

    @Transactional
    public TelemetryResponse record(TelemetryRequest request) {
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, request.deviceId())
                .eq(Device::getBound, 1));
        if (device == null) {
            throw new BusinessException(404, "设备不存在或尚未绑定");
        }

        LocalDateTime now = LocalDateTime.now();
        int threshold = device.getSmokeThreshold() == null ? 2000 : device.getSmokeThreshold();
        SmokeData duplicate = findDuplicate(request);
        if (duplicate != null) {
            return new TelemetryResponse(
                    true, true, duplicate, exceedsThreshold(duplicate.getConcentration(), threshold), null);
        }

        BigDecimal concentration = request.concentration().setScale(2, RoundingMode.HALF_UP);
        SmokeData smokeData = new SmokeData();
        smokeData.setDeviceId(device.getDeviceId());
        smokeData.setMessageId(normalizeMessageId(request.messageId()));
        smokeData.setConcentration(concentration);
        smokeData.setTimestamp(request.timestamp() == null ? now : request.timestamp());
        try {
            smokeDataMapper.insert(smokeData);
        } catch (DuplicateKeyException exception) {
            SmokeData concurrentDuplicate = findDuplicate(request);
            if (concurrentDuplicate != null) {
                return new TelemetryResponse(
                        true, true, concurrentDuplicate,
                        exceedsThreshold(concurrentDuplicate.getConcentration(), threshold), null);
            }
            throw exception;
        }

        device.setStatus(1);
        device.setLastHeartbeat(now);
        deviceMapper.updateById(device);
        alertService.resolveOfflineAlerts(device.getDeviceId());

        boolean thresholdExceeded = exceedsThreshold(concentration, threshold);
        var alert = thresholdExceeded
                ? alertService.createSmokeAlertIfAbsent(device, concentration, threshold)
                : null;
        return new TelemetryResponse(true, false, smokeData, thresholdExceeded, alert);
    }

    private SmokeData findDuplicate(TelemetryRequest request) {
        String messageId = normalizeMessageId(request.messageId());
        if (messageId == null) {
            return null;
        }
        return smokeDataMapper.selectOne(Wrappers.<SmokeData>lambdaQuery()
                .eq(SmokeData::getDeviceId, request.deviceId())
                .eq(SmokeData::getMessageId, messageId));
    }

    private String normalizeMessageId(String messageId) {
        return messageId == null || messageId.isBlank() ? null : messageId.trim();
    }

    private boolean exceedsThreshold(BigDecimal concentration, int threshold) {
        return concentration.compareTo(BigDecimal.valueOf(threshold)) >= 0;
    }
}
