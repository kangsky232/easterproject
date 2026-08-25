package com.smoke.service;

import com.smoke.dto.AlertReviewResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.entity.AlertReview;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.AlertRecordMapper;
import com.smoke.mapper.AlertReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlertReviewService {

    private final AlertRecordMapper alertRecordMapper;
    private final AlertReviewMapper alertReviewMapper;

    @Transactional
    public AlertReviewResponse review(Long alertId, String operator) {
        AlertRecord alert = alertRecordMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(404, "告警不存在");
        }
        AlertReview review = new AlertReview();
        review.setAlertId(alertId);
        review.setReviewType(AlertReview.TYPE_CONTEXT_REVIEW);
        review.setReviewResult(buildResult(alert));
        review.setOperatorName(operator);
        review.setCreatedAt(LocalDateTime.now());
        alertReviewMapper.insert(review);
        return new AlertReviewResponse(
                review.getId(), review.getAlertId(), review.getReviewType(), review.getReviewResult(),
                review.getOperatorName(), review.getCreatedAt());
    }

    private String buildResult(AlertRecord alert) {
        if (Integer.valueOf(1).equals(alert.getFalseAlarm())) {
            return "历史复核：该告警已标记为误报并归档，不代表当前存在火情。可结合现场记录检查误报原因与探测器环境。";
        }
        if (Integer.valueOf(AlertRecord.STATUS_RESOLVED).equals(alert.getStatus())) {
            if (alert.getAlertType() == AlertRecord.TYPE_OFFLINE) {
                return "历史复核：该设备离线告警已恢复并归档，不代表设备当前仍处于离线状态。";
            }
            int historicalConcentration = alert.getConcentration() == null ? 0 : alert.getConcentration();
            int historicalThreshold = alert.getThreshold() == null ? 0 : alert.getThreshold();
            return "历史复核：该烟雾告警已处置并归档，不代表当前仍存在火情。触发时浓度为 "
                    + historicalConcentration + "，阈值为 " + historicalThreshold + "。";
        }
        if (alert.getAlertType() == AlertRecord.TYPE_OFFLINE) {
            return "复核结论：设备已离线。请检查供电、网络与心跳上报，恢复后系统会自动关闭离线告警。";
        }
        int concentration = alert.getConcentration() == null ? 0 : alert.getConcentration();
        int threshold = alert.getThreshold() == null ? 0 : alert.getThreshold();
        if (threshold > 0 && concentration >= threshold * 2) {
            return "复核结论：高风险。浓度已超过阈值两倍，请立即疏散并确认现场火情。";
        }
        return "复核结论：烟雾浓度已超过阈值。请派人现场核验，并在排除火情后标记为误报。";
    }
}
