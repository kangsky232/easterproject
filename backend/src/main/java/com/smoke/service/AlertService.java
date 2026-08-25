package com.smoke.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.dto.PageResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.Device;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.AlertRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {

    private static final String SYSTEM_RECOVERY = "SYSTEM_RECOVERY";

    private final AlertRecordMapper alertRecordMapper;
    private final NotificationService notificationService;

    @Autowired
    public AlertService(AlertRecordMapper alertRecordMapper, NotificationService notificationService) {
        this.alertRecordMapper = alertRecordMapper;
        this.notificationService = notificationService;
    }

    AlertService(AlertRecordMapper alertRecordMapper) {
        this(alertRecordMapper, null);
    }

    public PageResponse<AlertRecord> list(
            String deviceId, Integer type, Integer status, int page, int pageSize) {
        validatePage(page, pageSize);
        if (type != null && type != AlertRecord.TYPE_SMOKE && type != AlertRecord.TYPE_OFFLINE) {
            throw new BusinessException(400, "type 只能是 1 或 2");
        }
        if (status != null
                && status != AlertRecord.STATUS_PENDING
                && status != AlertRecord.STATUS_CONFIRMED
                && status != AlertRecord.STATUS_RESOLVED) {
            throw new BusinessException(400, "status 只能是 0、1 或 2");
        }
        LambdaQueryWrapper<AlertRecord> query = Wrappers.lambdaQuery();
        query.eq(deviceId != null && !deviceId.isBlank(), AlertRecord::getDeviceId, deviceId)
                .eq(type != null, AlertRecord::getAlertType, type)
                .eq(status != null, AlertRecord::getStatus, status)
                .orderByDesc(AlertRecord::getCreatedAt);
        Page<AlertRecord> result = alertRecordMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResponse<>(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Transactional
    public AlertRecord confirm(Long id, String operator) {
        AlertRecord alert = requireAlert(id);
        if (Integer.valueOf(AlertRecord.STATUS_RESOLVED).equals(alert.getStatus())) {
            throw new BusinessException(409, "已处理的告警不能再次确认");
        }
        alert.setStatus(AlertRecord.STATUS_CONFIRMED);
        alert.setConfirmedBy(operator);
        alert.setConfirmedAt(LocalDateTime.now());
        alertRecordMapper.updateById(alert);
        return alert;
    }

    @Transactional
    public AlertRecord resolve(Long id, String operator) {
        AlertRecord alert = requireAlert(id);
        if (Integer.valueOf(AlertRecord.STATUS_RESOLVED).equals(alert.getStatus())) {
            return alert;
        }
        alert.setStatus(AlertRecord.STATUS_RESOLVED);
        alert.setConfirmedBy(operator);
        alert.setConfirmedAt(LocalDateTime.now());
        alertRecordMapper.updateById(alert);
        return alert;
    }

    @Transactional
    public AlertRecord markFalseAlarm(Long id, String operator) {
        AlertRecord alert = requireAlert(id);
        if (Integer.valueOf(1).equals(alert.getFalseAlarm())) {
            return alert;
        }
        if (Integer.valueOf(AlertRecord.STATUS_RESOLVED).equals(alert.getStatus())) {
            throw new BusinessException(409, "已处置的告警不能再标记为误报");
        }
        alert.setStatus(AlertRecord.STATUS_RESOLVED);
        alert.setFalseAlarm(1);
        alert.setConfirmedBy(operator);
        alert.setConfirmedAt(LocalDateTime.now());
        alertRecordMapper.updateById(alert);
        return alert;
    }

    @Transactional
    public AlertRecord createSmokeAlertIfAbsent(Device device, int concentration, int threshold) {
        return createIfAbsent(device.getDeviceId(), AlertRecord.TYPE_SMOKE, concentration, threshold);
    }

    @Transactional
    public AlertRecord createOfflineAlertIfAbsent(Device device) {
        return createIfAbsent(device.getDeviceId(), AlertRecord.TYPE_OFFLINE, null, null);
    }

    @Transactional
    public void resolveOfflineAlerts(String deviceId) {
        List<AlertRecord> activeAlerts = alertRecordMapper.selectList(activeQuery(deviceId, AlertRecord.TYPE_OFFLINE));
        resolveAlerts(activeAlerts, SYSTEM_RECOVERY);
    }

    @Transactional
    public void resolveDeviceAlerts(String deviceId, String operator) {
        List<AlertRecord> activeAlerts = alertRecordMapper.selectList(Wrappers.<AlertRecord>lambdaQuery()
                .eq(AlertRecord::getDeviceId, deviceId)
                .in(AlertRecord::getStatus, AlertRecord.STATUS_PENDING, AlertRecord.STATUS_CONFIRMED));
        resolveAlerts(activeAlerts, operator);
    }

    private void resolveAlerts(List<AlertRecord> activeAlerts, String operator) {
        LocalDateTime now = LocalDateTime.now();
        for (AlertRecord alert : activeAlerts) {
            alert.setStatus(AlertRecord.STATUS_RESOLVED);
            alert.setConfirmedBy(operator);
            alert.setConfirmedAt(now);
            alertRecordMapper.updateById(alert);
        }
    }

    private AlertRecord create(String deviceId, int type, Integer concentration, Integer threshold) {
        AlertRecord alert = new AlertRecord();
        alert.setDeviceId(deviceId);
        alert.setAlertType(type);
        alert.setConcentration(concentration);
        alert.setThreshold(threshold);
        alert.setStatus(AlertRecord.STATUS_PENDING);
        alert.setFalseAlarm(0);
        alert.setCreatedAt(LocalDateTime.now());
        alertRecordMapper.insert(alert);
        if (notificationService != null) {
            notificationService.createForAlert(alert);
        }
        return alert;
    }

    private AlertRecord createIfAbsent(String deviceId, int type, Integer concentration, Integer threshold) {
        AlertRecord active = findActive(deviceId, type);
        if (active != null) {
            return active;
        }
        try {
            return create(deviceId, type, concentration, threshold);
        } catch (DuplicateKeyException exception) {
            AlertRecord concurrentAlert = findActive(deviceId, type);
            if (concurrentAlert != null) {
                return concurrentAlert;
            }
            throw exception;
        }
    }

    private AlertRecord findActive(String deviceId, int type) {
        return alertRecordMapper.selectOne(activeQuery(deviceId, type).last("LIMIT 1"));
    }

    private LambdaQueryWrapper<AlertRecord> activeQuery(String deviceId, int type) {
        return Wrappers.<AlertRecord>lambdaQuery()
                .eq(AlertRecord::getDeviceId, deviceId)
                .eq(AlertRecord::getAlertType, type)
                .in(AlertRecord::getStatus, AlertRecord.STATUS_PENDING, AlertRecord.STATUS_CONFIRMED)
                .orderByDesc(AlertRecord::getCreatedAt);
    }

    private AlertRecord requireAlert(Long id) {
        AlertRecord alert = alertRecordMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(404, "告警不存在");
        }
        return alert;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
    }
}
