package com.smoke.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.dto.CreateBroadcastRequest;
import com.smoke.dto.PageResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.BroadcastLog;
import com.smoke.entity.Device;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.BroadcastLogMapper;
import com.smoke.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final BroadcastLogMapper broadcastLogMapper;
    private final DeviceMapper deviceMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final DingTalkMessageService dingTalkMessageService;

    public PageResponse<BroadcastLog> list(String deviceId, Integer status, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
        if (status != null
                && status != BroadcastLog.STATUS_PENDING
                && status != BroadcastLog.STATUS_SUCCESS
                && status != BroadcastLog.STATUS_FAILED) {
            throw new BusinessException(400, "status 只能是 0、1 或 2");
        }
        LambdaQueryWrapper<BroadcastLog> query = Wrappers.lambdaQuery();
        query.eq(deviceId != null && !deviceId.isBlank(), BroadcastLog::getDeviceId, deviceId)
                .eq(status != null, BroadcastLog::getStatus, status)
                .orderByDesc(BroadcastLog::getCreatedAt);
        Page<BroadcastLog> result = broadcastLogMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResponse<>(result.getRecords(), result.getTotal(), page, pageSize);
    }

    public BroadcastLog get(Long id) {
        return requireBroadcast(id);
    }

    @Transactional
    public BroadcastLog create(CreateBroadcastRequest request) {
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, request.deviceId())
                .eq(Device::getBound, 1));
        if (device == null) {
            throw new BusinessException(404, "广播目标设备不存在或已解绑");
        }
        if (request.triggerAlertId() != null) {
            AlertRecord alert = alertRecordMapper.selectById(request.triggerAlertId());
            if (alert == null || !request.deviceId().equals(alert.getDeviceId())) {
                throw new BusinessException(400, "触发告警与目标设备不匹配");
            }
        }

        BroadcastLog broadcast = new BroadcastLog();
        broadcast.setDeviceId(request.deviceId());
        broadcast.setContent(request.content());
        broadcast.setTriggerAlertId(request.triggerAlertId());
        broadcast.setStatus(BroadcastLog.STATUS_PENDING);
        broadcast.setCreatedAt(LocalDateTime.now());
        broadcastLogMapper.insert(broadcast);

        if (dingTalkMessageService.isConfigured()) {
            deliverToDingTalk(broadcast);
        }
        return broadcast;
    }

    @Transactional
    public BroadcastLog deliver(Long id) {
        BroadcastLog broadcast = requireBroadcast(id);
        if (!dingTalkMessageService.isConfigured()) {
            throw new BusinessException(503, "钉钉广播尚未配置，无法下发");
        }
        return deliverToDingTalk(broadcast);
    }

    @Transactional
    public BroadcastLog delete(Long id) {
        BroadcastLog broadcast = requireBroadcast(id);
        broadcastLogMapper.deleteById(id);
        return broadcast;
    }

    @Transactional
    public BroadcastLog updateStatus(Long id, Integer status) {
        BroadcastLog broadcast = requireBroadcast(id);
        if (!Integer.valueOf(BroadcastLog.STATUS_PENDING).equals(broadcast.getStatus())) {
            if (status.equals(broadcast.getStatus())) {
                return broadcast;
            }
            throw new BusinessException(409, "广播指令已经结束，不能修改结果");
        }
        broadcast.setStatus(status);
        broadcast.setExecutedAt(LocalDateTime.now());
        broadcastLogMapper.updateById(broadcast);
        return broadcast;
    }

    private BroadcastLog deliverToDingTalk(BroadcastLog broadcast) {
        try {
            dingTalkMessageService.sendBroadcast(broadcast.getDeviceId(), broadcast.getContent());
            broadcast.setStatus(BroadcastLog.STATUS_SUCCESS);
        } catch (DingTalkMessageService.DingTalkDeliveryException exception) {
            broadcast.setStatus(BroadcastLog.STATUS_FAILED);
            log.warn("DingTalk delivery failed for broadcast {}: {}", broadcast.getId(), exception.getMessage());
        }
        broadcast.setExecutedAt(LocalDateTime.now());
        broadcastLogMapper.updateById(broadcast);
        return broadcast;
    }

    private BroadcastLog requireBroadcast(Long id) {
        BroadcastLog broadcast = broadcastLogMapper.selectById(id);
        if (broadcast == null) {
            throw new BusinessException(404, "广播指令不存在");
        }
        return broadcast;
    }
}
