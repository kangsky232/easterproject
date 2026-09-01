package com.smoke.service;

import com.smoke.config.VisionProperties;
import com.smoke.entity.VisionEvent;
import com.smoke.mapper.VisionEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VisionPatrolServiceTest {

    @Test
    void automaticPatrolStartsPausedAndStopsScanningAfterPause() {
        VisionProperties properties = new VisionProperties();
        DeepSeekVisionClient client = mock(DeepSeekVisionClient.class);
        VisionEventMapper mapper = mock(VisionEventMapper.class);
        DingTalkMessageService dingTalk = mock(DingTalkMessageService.class);
        VisionPatrolService service = new VisionPatrolService(properties, client, mapper, dingTalk);
        VisionAnalysisResult normal = new VisionAnalysisResult(
                false, 0.10D, "LOW", "正常", "未见烟火", "SIMULATION_FALLBACK",
                "built-in-scenario-rules", null);
        when(client.analyze(any())).thenReturn(normal);

        assertFalse(service.status().running());
        service.scheduledScan();
        verifyNoInteractions(client);

        assertTrue(service.startPatrol().running());
        service.scheduledScan();
        verify(client).analyze(any());

        assertFalse(service.pausePatrol().running());
        service.scheduledScan();
        verify(client, times(1)).analyze(any());
    }

    @Test
    void suspiciousFrameCreatesPendingEventAndSendsDingTalkAlert() {
        VisionProperties properties = new VisionProperties();
        DeepSeekVisionClient client = mock(DeepSeekVisionClient.class);
        VisionEventMapper mapper = mock(VisionEventMapper.class);
        DingTalkMessageService dingTalk = mock(DingTalkMessageService.class);
        VisionPatrolService service = new VisionPatrolService(properties, client, mapper, dingTalk);
        VisionAnalysisResult normal = new VisionAnalysisResult(
                false, 0.10D, "LOW", "正常", "未见烟火", "SIMULATION_FALLBACK",
                "built-in-scenario-rules", null);
        VisionAnalysisResult suspicious = new VisionAnalysisResult(
                true, 0.93D, "HIGH", "发现疑似烟雾", "灰白色烟雾扩散", "DEEPSEEK_VISION",
                "deepseek-v4-flash-vision-exp", null);
        when(client.analyze(any())).thenReturn(normal, normal, suspicious);
        when(dingTalk.isConfigured()).thenReturn(true);
        when(dingTalk.sendVisionAlert(any(), any())).thenReturn(2);
        doAnswer(invocation -> {
            VisionEvent event = invocation.getArgument(0);
            event.setId(31L);
            return 1;
        }).when(mapper).insert(any(VisionEvent.class));

        service.analyzeNextFrame();
        service.analyzeNextFrame();
        service.analyzeNextFrame();

        ArgumentCaptor<VisionEvent> eventCaptor = ArgumentCaptor.forClass(VisionEvent.class);
        verify(mapper).insert(eventCaptor.capture());
        VisionEvent event = eventCaptor.getValue();
        assertNotNull(event.getCameraCode());
        assertTrue(event.getFloorNo() >= 1);
        assertTrue(event.getImageUrl().contains("/vision-patrol/"));
        assertEquals(VisionEvent.STATUS_PENDING_REVIEW, event.getStatus());
        assertEquals(VisionEvent.MODE_DEEPSEEK, event.getDetectionMode());
        assertEquals(new BigDecimal("0.9300"), event.getConfidence());
        assertEquals(VisionEvent.NOTICE_SENT, event.getDingtalkStatus());
        assertEquals(2, event.getDingtalkRecipients());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(dingTalk).sendVisionAlert(eq(event.getCameraCode()), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("模拟轮播图片（非真实摄像头）"));
        assertTrue(messageCaptor.getValue().contains("DeepSeek Vision（输入仍为模拟轮播图片，非真实摄像头）"));
    }

    @Test
    void allFifteenFramesAreVisitedOnceBeforeTheRandomOrderRepeats() {
        VisionProperties properties = new VisionProperties();
        DeepSeekVisionClient client = mock(DeepSeekVisionClient.class);
        VisionEventMapper mapper = mock(VisionEventMapper.class);
        DingTalkMessageService dingTalk = mock(DingTalkMessageService.class);
        VisionPatrolService service = new VisionPatrolService(properties, client, mapper, dingTalk);
        VisionAnalysisResult normal = new VisionAnalysisResult(
                false, 0.10D, "LOW", "正常", "未见烟火", "SIMULATION_FALLBACK",
                "built-in-scenario-rules", null);
        when(client.analyze(any())).thenReturn(normal);

        for (int index = 0; index < 16; index++) service.analyzeNextFrame();

        ArgumentCaptor<SimulatedVisionFrame> frameCaptor = ArgumentCaptor.forClass(SimulatedVisionFrame.class);
        verify(client, times(16)).analyze(frameCaptor.capture());
        var frames = frameCaptor.getAllValues();
        assertEquals(15, frames.subList(0, 15).stream()
                .map(SimulatedVisionFrame::frameKey)
                .distinct()
                .count());
        assertTrue(!frames.get(14).frameKey().equals(frames.get(15).frameKey()));
    }

    @Test
    void pendingEventCanBeReviewedOnceAndPublishesReviewNotice() {
        VisionProperties properties = new VisionProperties();
        DeepSeekVisionClient client = mock(DeepSeekVisionClient.class);
        VisionEventMapper mapper = mock(VisionEventMapper.class);
        DingTalkMessageService dingTalk = mock(DingTalkMessageService.class);
        VisionPatrolService service = new VisionPatrolService(properties, client, mapper, dingTalk);
        VisionEvent event = pendingEvent(9L);
        when(mapper.selectById(9L)).thenReturn(event);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(dingTalk.isConfigured()).thenReturn(true);
        when(dingTalk.sendVisionReview(any(), any())).thenReturn(2);

        var response = service.review(9L, VisionEvent.STATUS_CONFIRMED_FIRE,
                "现场人员确认配电箱冒烟", "firefighter");

        assertEquals(VisionEvent.STATUS_CONFIRMED_FIRE, response.status());
        assertEquals("firefighter", response.reviewerUsername());
        assertEquals("现场人员确认配电箱冒烟", response.reviewRemark());
        assertNotNull(response.reviewedAt());
        verify(mapper).update(isNull(), any());
        verify(dingTalk).sendVisionReview(eq("A2-06F-C03"), any());
    }

    private VisionEvent pendingEvent(Long id) {
        LocalDateTime now = LocalDateTime.now();
        VisionEvent event = new VisionEvent();
        event.setId(id);
        event.setEventNo("AI-20260901-TEST0001");
        event.setCameraCode("A2-06F-C03");
        event.setLocation("2号楼6层配电间外");
        event.setBuildingCode("A2");
        event.setFloorNo(6);
        event.setFrameKey("electrical-smoke-warning");
        event.setImageUrl("https://example.com/electrical-smoke.jpg");
        event.setDetectionMode(VisionEvent.MODE_DEEPSEEK);
        event.setModelName("deepseek-v4-flash-vision-exp");
        event.setRiskLevel("HIGH");
        event.setConfidence(new BigDecimal("0.9400"));
        event.setSummary("发现疑似烟雾");
        event.setEvidence("配电箱附近出现浓烟");
        event.setStatus(VisionEvent.STATUS_PENDING_REVIEW);
        event.setDingtalkStatus(VisionEvent.NOTICE_SENT);
        event.setDingtalkRecipients(2);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }
}
