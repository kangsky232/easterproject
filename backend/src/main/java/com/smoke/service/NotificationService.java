package com.smoke.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.dto.NotificationResponse;
import com.smoke.dto.NotificationSummaryResponse;
import com.smoke.dto.PageResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.NotificationLog;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.NotificationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> CHANNELS = Set.of(
            NotificationLog.CHANNEL_APP,
            NotificationLog.CHANNEL_SMS);
    private static final Set<String> STATUSES = Set.of(
            NotificationLog.STATUS_PENDING,
            NotificationLog.STATUS_SENT,
            NotificationLog.STATUS_FAILED);

    private final NotificationLogMapper notificationLogMapper;

    /**
     * Creates local delivery records without claiming that an external SMS was sent.
     * A future delivery adapter can update pending records to SENT or FAILED without changing the API.
     */
    public void createForAlert(AlertRecord alert) {
        String type = alert.getAlertType() == AlertRecord.TYPE_SMOKE ? "烟雾超阈值" : "设备离线";
        String content = "设备 " + alert.getDeviceId() + " 触发" + type + "告警，请及时处理。";
        create(alert, NotificationLog.CHANNEL_APP, content, NotificationLog.STATUS_SENT);
        create(alert, NotificationLog.CHANNEL_SMS, content, NotificationLog.STATUS_PENDING);
    }

    public PageResponse<NotificationResponse> list(
            int page,
            int pageSize,
            Long alertId,
            String deviceId,
            String channel,
            String status) {
        validatePage(page, pageSize);
        String normalizedDeviceId = normalizeText(deviceId);
        String normalizedChannel = normalizeCode(channel);
        String normalizedStatus = normalizeCode(status);
        validateChannel(normalizedChannel);
        validateStatus(normalizedStatus);
        LambdaQueryWrapper<NotificationLog> query = Wrappers.<NotificationLog>lambdaQuery()
                .eq(alertId != null, NotificationLog::getAlertId, alertId)
                .eq(normalizedDeviceId != null, NotificationLog::getDeviceId, normalizedDeviceId)
                .eq(normalizedChannel != null, NotificationLog::getChannel, normalizedChannel)
                .eq(normalizedStatus != null, NotificationLog::getStatus, normalizedStatus)
                .orderByDesc(NotificationLog::getCreatedAt);
        Page<NotificationLog> result = notificationLogMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResponse<>(
                result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(),
                page,
                pageSize);
    }

    public NotificationResponse get(Long id) {
        NotificationLog notification = notificationLogMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(404, "通知记录不存在");
        }
        return toResponse(notification);
    }

    public NotificationSummaryResponse summary() {
        return new NotificationSummaryResponse(
                count(Wrappers.<NotificationLog>lambdaQuery()),
                count(Wrappers.<NotificationLog>lambdaQuery().eq(NotificationLog::getChannel, NotificationLog.CHANNEL_APP)),
                count(Wrappers.<NotificationLog>lambdaQuery().eq(NotificationLog::getChannel, NotificationLog.CHANNEL_SMS)),
                count(Wrappers.<NotificationLog>lambdaQuery().eq(NotificationLog::getStatus, NotificationLog.STATUS_PENDING)),
                count(Wrappers.<NotificationLog>lambdaQuery().eq(NotificationLog::getStatus, NotificationLog.STATUS_SENT)),
                count(Wrappers.<NotificationLog>lambdaQuery().eq(NotificationLog::getStatus, NotificationLog.STATUS_FAILED)));
    }

    private void create(AlertRecord alert, String channel, String content, String status) {
        LocalDateTime now = LocalDateTime.now();
        NotificationLog notification = new NotificationLog();
        notification.setAlertId(alert.getId());
        notification.setDeviceId(alert.getDeviceId());
        notification.setChannel(channel);
        notification.setReceiver(NotificationLog.CHANNEL_SMS.equals(channel) ? "未配置" : "系统管理员");
        notification.setContent(content);
        notification.setStatus(status);
        notification.setSentAt(NotificationLog.STATUS_SENT.equals(status) ? now : null);
        notification.setCreatedAt(now);
        notificationLogMapper.insert(notification);
    }

    private long count(LambdaQueryWrapper<NotificationLog> query) {
        return notificationLogMapper.selectCount(query);
    }

    private NotificationResponse toResponse(NotificationLog notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAlertId(),
                notification.getDeviceId(),
                notification.getChannel(),
                notification.getReceiver(),
                notification.getContent(),
                notification.getStatus(),
                notification.getSentAt(),
                notification.getCreatedAt());
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
    }

    private void validateChannel(String channel) {
        if (StringUtils.hasText(channel) && !CHANNELS.contains(channel)) {
            throw new BusinessException(400, "channel 仅支持 APP 或 SMS");
        }
    }

    private void validateStatus(String status) {
        if (StringUtils.hasText(status) && !STATUSES.contains(status)) {
            throw new BusinessException(400, "status 仅支持 PENDING、SENT 或 FAILED");
        }
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
