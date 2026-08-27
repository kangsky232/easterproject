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
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final DeviceMapper deviceMapper;
    private final SmokeDataMapper smokeDataMapper;
    private final AlertService alertService;
    private final TelemetryAlertEvaluator alertEvaluator;

    @Transactional
    public TelemetryResponse record(TelemetryRequest request) {
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, request.deviceId())
                .eq(Device::getBound, 1));
        if (device == null) {
            throw new BusinessException(404, "设备不存在或尚未绑定");
        }

        LocalDateTime now = LocalDateTime.now();
        SmokeData duplicate = findDuplicate(request);
        if (duplicate != null) {
            return new TelemetryResponse(
                    true, true, duplicate,
                    !alertEvaluator.evaluate(duplicate, null, device.getSmokeThreshold()).isEmpty(), null);
        }

        SmokeData previous = latestReading(device.getDeviceId());

        BigDecimal concentration = request.concentration().setScale(2, RoundingMode.HALF_UP);
        SmokeData smokeData = new SmokeData();
        smokeData.setDeviceId(device.getDeviceId());
        smokeData.setMessageId(normalizeMessageId(request.messageId()));
        smokeData.setConcentration(concentration);
        smokeData.setTemperature(twoDecimals(request.temperature()));
        smokeData.setHumidity(twoDecimals(request.humidity()));
        smokeData.setCurrentValue(twoDecimals(request.current()));
        smokeData.setWireTemperature(twoDecimals(request.wireTemperature()));
        smokeData.setCoValue(twoDecimals(request.coValue()));
        smokeData.setBeepStatus(normalizeBeepStatus(request.beepStatus()));
        smokeData.setTimestamp(request.timestamp() == null ? now : request.timestamp());
        try {
            smokeDataMapper.insert(smokeData);
        } catch (DuplicateKeyException exception) {
            SmokeData concurrentDuplicate = findDuplicate(request);
            if (concurrentDuplicate != null) {
                return new TelemetryResponse(
                        true, true, concurrentDuplicate,
                        !alertEvaluator.evaluate(concurrentDuplicate, null, device.getSmokeThreshold()).isEmpty(), null);
            }
            throw exception;
        }

        device.setStatus(1);
        device.setLastHeartbeat(now);
        deviceMapper.updateById(device);
        alertService.resolveOfflineAlerts(device.getDeviceId());

        List<TelemetryAlertEvaluator.AlertSignal> signals =
                alertEvaluator.evaluate(smokeData, previous, device.getSmokeThreshold());
        List<com.smoke.entity.AlertRecord> alerts = alertService.createSensorAlerts(device, signals);
        boolean thresholdExceeded = !signals.isEmpty();
        var alert = alerts.isEmpty() ? null : alerts.get(0);
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

    private SmokeData latestReading(String deviceId) {
        return smokeDataMapper.selectOne(Wrappers.<SmokeData>lambdaQuery()
                .eq(SmokeData::getDeviceId, deviceId)
                .orderByDesc(SmokeData::getTimestamp)
                .orderByDesc(SmokeData::getId)
                .last("LIMIT 1"));
    }

    private BigDecimal twoDecimals(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeBeepStatus(String beepStatus) {
        return beepStatus == null || beepStatus.isBlank()
                ? null
                : beepStatus.trim().toUpperCase(Locale.ROOT);
    }
}
