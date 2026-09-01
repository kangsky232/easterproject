package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.config.VisionProperties;
import com.smoke.dto.PageResponse;
import com.smoke.dto.VisionAnalysisResponse;
import com.smoke.dto.VisionEventResponse;
import com.smoke.dto.VisionFrameResponse;
import com.smoke.dto.VisionStatusResponse;
import com.smoke.dto.VisionSummaryResponse;
import com.smoke.entity.VisionEvent;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.VisionEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionPatrolService {

    private static final Set<String> EVENT_STATUSES = Set.of(
            VisionEvent.STATUS_PENDING_REVIEW,
            VisionEvent.STATUS_CONFIRMED_FIRE,
            VisionEvent.STATUS_FALSE_ALARM);

    private final VisionProperties properties;
    private final DeepSeekVisionClient deepSeekVisionClient;
    private final VisionEventMapper visionEventMapper;
    private final DingTalkMessageService dingTalkMessageService;
    private final List<Integer> frameOrder = new ArrayList<>();
    private int frameOrderCursor;
    private int lastFrameIndex = -1;

    private volatile VisionFrameResponse currentFrame;
    private volatile VisionAnalysisResponse latestAnalysis;
    private volatile Long latestEventId;
    private volatile boolean patrolRunning;
    private volatile boolean scanning;

    @Scheduled(
            fixedDelayString = "${app.vision.interval-ms:15000}",
            initialDelayString = "${app.vision.initial-delay-ms:3000}")
    public synchronized void scheduledScan() {
        if (!properties.isEnabled() || !patrolRunning) return;
        analyzeNextFrame();
    }

    public synchronized VisionStatusResponse startPatrol() {
        if (properties.isEnabled()) patrolRunning = true;
        return status();
    }

    public synchronized VisionStatusResponse pausePatrol() {
        patrolRunning = false;
        return status();
    }

    public synchronized VisionStatusResponse analyzeNextFrame() {
        if (!properties.isEnabled()) return status();
        List<SimulatedVisionFrame> frames = frames();
        SimulatedVisionFrame frame = nextFrame(frames);
        LocalDateTime capturedAt = LocalDateTime.now();
        currentFrame = new VisionFrameResponse(
                frame.frameKey(), frame.cameraCode(), frame.location(), frame.buildingCode(),
                frame.floorNo(), frame.imageUrl(), capturedAt);
        scanning = true;
        try {
            VisionAnalysisResult result = deepSeekVisionClient.analyze(frame);
            LocalDateTime analyzedAt = LocalDateTime.now();
            latestAnalysis = new VisionAnalysisResponse(
                    result.suspectedFire(), result.confidence(), result.riskLevel(), result.summary(),
                    result.evidence(), result.mode(), result.model(), result.error(), analyzedAt);
            if (result.suspectedFire() && result.confidence() >= properties.getConfidenceThreshold()) {
                VisionEvent event = createEventIfAbsent(frame, result, analyzedAt);
                if (event != null) latestEventId = event.getId();
            }
        } finally {
            scanning = false;
        }
        return status();
    }

    public VisionStatusResponse status() {
        VisionEventResponse latestEvent = latestEventId == null
                ? latestEvent()
                : toResponse(visionEventMapper.selectById(latestEventId));
        return new VisionStatusResponse(
                properties.isEnabled(),
                patrolRunning,
                scanning,
                properties.isDeepSeekConfigured(),
                capability(),
                properties.isDeepSeekConfigured() ? "DEEPSEEK" : "BUILT_IN_SIMULATION",
                properties.isDeepSeekConfigured() ? properties.getModel() : "built-in-scenario-rules",
                properties.getIntervalMs(),
                properties.getConfidenceThreshold(),
                currentFrame,
                latestAnalysis,
                latestEvent);
    }

    public String capability() {
        if (!properties.isEnabled()) return "DISABLED";
        return properties.isDeepSeekConfigured() ? "DEEPSEEK_VISION" : "SIMULATION_FALLBACK";
    }

    public PageResponse<VisionEventResponse> list(String status, int page, int pageSize) {
        validatePage(page, pageSize);
        String normalizedStatus = normalizeStatus(status);
        Page<VisionEvent> result = visionEventMapper.selectPage(
                new Page<>(page, pageSize),
                Wrappers.<VisionEvent>lambdaQuery()
                        .eq(normalizedStatus != null, VisionEvent::getStatus, normalizedStatus)
                        .orderByDesc(VisionEvent::getCreatedAt)
                        .orderByDesc(VisionEvent::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(), page, pageSize);
    }

    public VisionSummaryResponse summary() {
        long pending = count(VisionEvent.STATUS_PENDING_REVIEW);
        long confirmed = count(VisionEvent.STATUS_CONFIRMED_FIRE);
        long falseAlarm = count(VisionEvent.STATUS_FALSE_ALARM);
        return new VisionSummaryResponse(pending, confirmed, falseAlarm, pending + confirmed + falseAlarm);
    }

    public VisionEventResponse review(Long id, String verdict, String remark, String reviewer) {
        VisionEvent event = visionEventMapper.selectById(id);
        if (event == null) throw new BusinessException(404, "视觉事件不存在");
        if (!VisionEvent.STATUS_PENDING_REVIEW.equals(event.getStatus())) {
            throw new BusinessException(409, "该视觉事件已经完成人工判断，不能重复覆盖结论");
        }
        String normalizedVerdict = normalizeStatus(verdict);
        if (!Set.of(VisionEvent.STATUS_CONFIRMED_FIRE, VisionEvent.STATUS_FALSE_ALARM)
                .contains(normalizedVerdict)) {
            throw new BusinessException(400, "verdict 仅支持 CONFIRMED_FIRE 或 FALSE_ALARM");
        }
        String normalizedRemark = normalizeRequired(remark, "人工判断依据不能为空", 500);
        String normalizedReviewer = normalizeRequired(reviewer, "无法识别当前操作账号", 64);
        LocalDateTime reviewedAt = LocalDateTime.now();
        int updated = visionEventMapper.update(null, Wrappers.<VisionEvent>update()
                .eq("id", id)
                .eq("status", VisionEvent.STATUS_PENDING_REVIEW)
                .set("status", normalizedVerdict)
                .set("reviewer_username", normalizedReviewer)
                .set("review_remark", normalizedRemark)
                .set("reviewed_at", reviewedAt)
                .set("updated_at", reviewedAt));
        if (updated != 1) {
            throw new BusinessException(409, "视觉事件状态已变化，请刷新后重试");
        }
        event.setStatus(normalizedVerdict);
        event.setReviewerUsername(normalizedReviewer);
        event.setReviewRemark(normalizedRemark);
        event.setReviewedAt(reviewedAt);
        event.setUpdatedAt(reviewedAt);
        sendReviewNotice(event);
        return toResponse(event);
    }

    private VisionEvent createEventIfAbsent(
            SimulatedVisionFrame frame, VisionAnalysisResult result, LocalDateTime detectedAt) {
        VisionEvent existing = visionEventMapper.selectOne(
                Wrappers.<VisionEvent>lambdaQuery()
                        .eq(VisionEvent::getCameraCode, frame.cameraCode())
                        .eq(VisionEvent::getStatus, VisionEvent.STATUS_PENDING_REVIEW)
                        .last("LIMIT 1"));
        if (existing != null) return existing;

        VisionEvent event = new VisionEvent();
        event.setEventNo(createEventNo());
        event.setCameraCode(frame.cameraCode());
        event.setLocation(frame.location());
        event.setBuildingCode(frame.buildingCode());
        event.setFloorNo(frame.floorNo());
        event.setFrameKey(frame.frameKey());
        event.setImageUrl(frame.imageUrl());
        event.setDetectionMode(result.mode());
        event.setModelName(result.model());
        event.setRiskLevel(result.riskLevel());
        event.setConfidence(BigDecimal.valueOf(result.confidence()).setScale(4, RoundingMode.HALF_UP));
        event.setSummary(result.summary());
        event.setEvidence(result.evidence());
        event.setStatus(VisionEvent.STATUS_PENDING_REVIEW);
        event.setDingtalkStatus(VisionEvent.NOTICE_PENDING);
        event.setCreatedAt(detectedAt);
        event.setUpdatedAt(detectedAt);
        visionEventMapper.insert(event);
        sendDetectionNotice(event);
        return event;
    }

    private void sendDetectionNotice(VisionEvent event) {
        if (!dingTalkMessageService.isConfigured()) {
            event.setDingtalkStatus(VisionEvent.NOTICE_SKIPPED);
            event.setDingtalkError("钉钉未配置");
            visionEventMapper.updateById(event);
            return;
        }
        String sourceNotice = VisionEvent.MODE_SIMULATION.equals(event.getDetectionMode())
                ? "模拟演示规则（非真实摄像头、非 DeepSeek 结论）"
                : "DeepSeek Vision（输入仍为模拟轮播图片，非真实摄像头）";
        String content = "事件：" + event.getEventNo()
                + "\n位置：" + event.getLocation()
                + "\n画面来源：模拟轮播图片（非真实摄像头）"
                + "\n识别方式：" + sourceNotice
                + "\n风险：" + event.getRiskLevel()
                + "（置信度 " + event.getConfidence().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%）"
                + "\n结论：" + event.getSummary()
                + "\n依据：" + event.getEvidence()
                + "\n画面：" + event.getImageUrl()
                + "\n请登录系统进行人工判断，AI 结果不能替代现场核验。";
        try {
            int recipients = dingTalkMessageService.sendVisionAlert(event.getCameraCode(), content);
            event.setDingtalkStatus(VisionEvent.NOTICE_SENT);
            event.setDingtalkRecipients(recipients);
            event.setDingtalkError(null);
        } catch (DingTalkMessageService.DingTalkDeliveryException exception) {
            event.setDingtalkStatus(VisionEvent.NOTICE_FAILED);
            event.setDingtalkError(limit(exception.getMessage(), 500));
            log.warn("Vision event {} DingTalk delivery failed: {}", event.getEventNo(), exception.getMessage());
        }
        event.setUpdatedAt(LocalDateTime.now());
        visionEventMapper.updateById(event);
    }

    private void sendReviewNotice(VisionEvent event) {
        if (!dingTalkMessageService.isConfigured()) return;
        String result = VisionEvent.STATUS_CONFIRMED_FIRE.equals(event.getStatus()) ? "人工确认为火情" : "人工判断为误报";
        String content = "事件：" + event.getEventNo()
                + "\n结果：" + result
                + "\n位置：" + event.getLocation()
                + "\n判断人：" + event.getReviewerUsername()
                + "\n依据：" + event.getReviewRemark();
        try {
            dingTalkMessageService.sendVisionReview(event.getCameraCode(), content);
        } catch (DingTalkMessageService.DingTalkDeliveryException exception) {
            log.warn("Vision event {} review notice failed: {}", event.getEventNo(), exception.getMessage());
        }
    }

    private VisionEventResponse latestEvent() {
        VisionEvent event = visionEventMapper.selectOne(
                Wrappers.<VisionEvent>lambdaQuery()
                        .orderByDesc(VisionEvent::getCreatedAt)
                        .orderByDesc(VisionEvent::getId)
                        .last("LIMIT 1"));
        if (event != null) latestEventId = event.getId();
        return toResponse(event);
    }

    private long count(String status) {
        return visionEventMapper.selectCount(
                Wrappers.<VisionEvent>lambdaQuery().eq(VisionEvent::getStatus, status));
    }

    private VisionEventResponse toResponse(VisionEvent event) {
        if (event == null) return null;
        return new VisionEventResponse(
                event.getId(), event.getEventNo(), event.getCameraCode(), event.getLocation(),
                event.getBuildingCode(), event.getFloorNo(), event.getFrameKey(), event.getImageUrl(),
                event.getDetectionMode(), event.getModelName(), event.getRiskLevel(), event.getConfidence(),
                event.getSummary(), event.getEvidence(), event.getStatus(), event.getDingtalkStatus(),
                event.getDingtalkRecipients(), event.getDingtalkError(), event.getReviewerUsername(),
                event.getReviewRemark(), event.getReviewedAt(), event.getCreatedAt(), event.getUpdatedAt());
    }

    private SimulatedVisionFrame nextFrame(List<SimulatedVisionFrame> frames) {
        if (frameOrderCursor >= frameOrder.size()) {
            frameOrder.clear();
            for (int index = 0; index < frames.size(); index++) frameOrder.add(index);
            Collections.shuffle(frameOrder);
            if (frameOrder.size() > 1 && frameOrder.get(0) == lastFrameIndex) {
                Collections.swap(frameOrder, 0, 1);
            }
            frameOrderCursor = 0;
        }
        int index = frameOrder.get(frameOrderCursor++);
        lastFrameIndex = index;
        return frames.get(index);
    }

    private List<SimulatedVisionFrame> frames() {
        String base = trimTrailingSlash(properties.getFrameBaseUrl()) + "/vision-patrol/";
        return List.of(
                new SimulatedVisionFrame(
                        "lobby-normal", "A1-01F-C03", "1号住宅楼 1层电梯门厅", "A1", 1,
                        base + "01-lobby-normal.jpg", false,
                        "门厅画面未发现烟火迹象", "电梯门厅清晰，未见明火或异常烟雾"),
                new SimulatedVisionFrame(
                        "parking-normal", "A1-B1-C01", "1号住宅楼 地下停车连接区", "A1", 1,
                        base + "02-parking-normal.jpg", false,
                        "停车连接区未发现烟火迹象", "车库能见度正常，未见烟雾或异常亮点"),
                new SimulatedVisionFrame(
                        "bicycle-room-normal", "A2-01F-C04", "2号住宅楼 1层非机动车存放间", "A2", 1,
                        base + "03-bicycle-room-normal.jpg", false,
                        "非机动车存放间未发现烟火迹象", "车辆区域轮廓清晰，未见烟雾或火焰"),
                new SimulatedVisionFrame(
                        "electrical-room-normal", "A3-02F-C02", "3号住宅楼 2层配电设备间", "A3", 2,
                        base + "04-electrical-room-normal.jpg", false,
                        "配电设备间未发现烟火迹象", "柜体与通道清晰，未见烟雾、火花或异常亮点"),
                new SimulatedVisionFrame(
                        "stairwell-night-normal", "A1-04F-C02", "1号住宅楼 4层消防楼梯", "A1", 4,
                        base + "05-stairwell-night-normal.jpg", false,
                        "夜间楼梯画面未发现烟火迹象", "楼梯和防火门轮廓清晰，未见烟雾聚集"),
                new SimulatedVisionFrame(
                        "rooftop-normal", "A2-08F-C04", "2号住宅楼 8层屋面出口", "A2", 8,
                        base + "06-rooftop-normal.jpg", false,
                        "屋面出口未发现烟火迹象", "出口平台能见度正常，未见烟雾或火焰"),
                new SimulatedVisionFrame(
                        "elevator-lobby-normal", "A3-03F-C02", "3号住宅楼 3层电梯前室", "A3", 3,
                        base + "07-elevator-lobby-normal.jpg", false,
                        "电梯前室未发现烟火迹象", "前室清晰，未见异常烟雾扩散"),
                new SimulatedVisionFrame(
                        "rain-passage-normal", "A1-01F-C04", "1号住宅楼 1层室外连廊", "A1", 1,
                        base + "08-rain-passage-normal.jpg", false,
                        "雨天连廊未发现烟火迹象", "画面中的低对比区域来自雨水反光，未见烟雾聚集"),
                new SimulatedVisionFrame(
                        "old-hallway-normal", "A3-02F-C03", "3号住宅楼 2层旧式过道", "A3", 2,
                        base + "09-old-hallway-normal.jpg", false,
                        "旧式过道未发现烟火迹象", "管线和楼梯区域清晰，未见异常烟雾"),
                new SimulatedVisionFrame(
                        "rental-corridor-normal", "A2-05F-C04", "2号住宅楼 5层出租房过道", "A2", 5,
                        base + "10-rental-corridor-normal.jpg", false,
                        "出租房过道未发现烟火迹象", "房门与通道能见度正常，未见烟雾或火焰"),
                new SimulatedVisionFrame(
                        "kitchen-smoke", "A1-02F-C05", "1号住宅楼 2层活动室厨房外", "A1", 2,
                        base + "11-kitchen-smoke.jpg", true,
                        "厨房门口出现疑似烟雾，需要人工复核", "灰白色烟雾从厨房门缝及门前区域持续扩散"),
                new SimulatedVisionFrame(
                        "waste-room-smoke", "A2-01F-C05", "2号住宅楼 1层垃圾收集间", "A2", 1,
                        base + "12-waste-room-smoke.jpg", true,
                        "垃圾收集间出现疑似阴燃烟雾，需要人工复核", "一个垃圾桶上方存在连续灰白色烟柱"),
                new SimulatedVisionFrame(
                        "electrical-cabinet-fire", "A3-02F-C06", "3号住宅楼 2层配电柜通道", "A3", 2,
                        base + "13-electrical-cabinet-fire.jpg", true,
                        "配电柜出现疑似电气火情，需要立即人工复核", "柜体下沿存在深灰烟雾并伴随橙色异常亮点"),
                new SimulatedVisionFrame(
                        "stairwell-smoke", "A1-04F-C06", "1号住宅楼 4层消防楼梯", "A1", 4,
                        base + "14-stairwell-smoke.jpg", true,
                        "消防楼梯出现大量疑似烟雾，需要立即人工复核", "灰色烟雾由下层楼梯向上扩散，能见度明显下降"),
                new SimulatedVisionFrame(
                        "ebike-charging-fire", "A2-B1-C06", "2号住宅楼 地下电动车充电区", "A2", 1,
                        base + "15-ebike-charging-fire.jpg", true,
                        "电动车充电区出现疑似明火和浓烟，需要立即人工复核", "车辆充电位可见橙色火焰与上升的深色浓烟"));
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!EVENT_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "status 不正确");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new BusinessException(400, message);
        }
        return normalized;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
    }

    private String createEventNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "AI-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.isBlank() ? "https://easterproject.pages.dev" : normalized;
    }

    private String limit(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
