package com.smoke.service;

import com.smoke.dto.AlertReviewResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.AlertReview;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.AlertReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertReviewServiceTest {

    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private AlertReviewMapper alertReviewMapper;

    @Test
    void highConcentrationReviewReturnsHighRiskConclusion() {
        AlertRecord alert = new AlertRecord();
        alert.setId(12L);
        alert.setAlertType(AlertRecord.TYPE_SMOKE);
        alert.setConcentration(new BigDecimal("4000.25"));
        alert.setThreshold(2000);
        when(alertRecordMapper.selectById(12L)).thenReturn(alert);
        AlertReviewService service = new AlertReviewService(alertRecordMapper, alertReviewMapper);

        AlertReviewResponse response = service.review(12L, "security-user");

        assertTrue(response.reviewResult().contains("高风险"));
        ArgumentCaptor<AlertReview> captor = ArgumentCaptor.forClass(AlertReview.class);
        verify(alertReviewMapper).insert(captor.capture());
        assertTrue(captor.getValue().getReviewResult().contains("高风险"));
    }

    @Test
    void resolvedAlertReviewIsExplicitlyHistorical() {
        AlertRecord alert = new AlertRecord();
        alert.setId(13L);
        alert.setAlertType(AlertRecord.TYPE_SMOKE);
        alert.setConcentration(new BigDecimal("5000.25"));
        alert.setThreshold(2000);
        alert.setStatus(AlertRecord.STATUS_RESOLVED);
        alert.setFalseAlarm(0);
        when(alertRecordMapper.selectById(13L)).thenReturn(alert);
        AlertReviewService service = new AlertReviewService(alertRecordMapper, alertReviewMapper);

        AlertReviewResponse response = service.review(13L, "security-user");

        assertTrue(response.reviewResult().contains("历史复核"));
        assertTrue(response.reviewResult().contains("已处置并归档"));
        assertTrue(response.reviewResult().contains("不代表当前仍存在火情"));
    }

    @Test
    void falseAlarmReviewDoesNotClaimCurrentRisk() {
        AlertRecord alert = new AlertRecord();
        alert.setId(14L);
        alert.setAlertType(AlertRecord.TYPE_SMOKE);
        alert.setConcentration(new BigDecimal("5000.25"));
        alert.setThreshold(2000);
        alert.setStatus(AlertRecord.STATUS_RESOLVED);
        alert.setFalseAlarm(1);
        when(alertRecordMapper.selectById(14L)).thenReturn(alert);
        AlertReviewService service = new AlertReviewService(alertRecordMapper, alertReviewMapper);

        AlertReviewResponse response = service.review(14L, "security-user");

        assertTrue(response.reviewResult().contains("已标记为误报"));
        assertTrue(response.reviewResult().contains("不代表当前存在火情"));
    }
}
