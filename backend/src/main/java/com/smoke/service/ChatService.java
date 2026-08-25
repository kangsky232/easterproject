package com.smoke.service;

import com.smoke.dto.ChatResponse;
import com.smoke.entity.AlertRecord;
import com.smoke.mapper.AlertRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ChatService {

    private final AlertRecordMapper alertRecordMapper;
    private final RagClient ragClient;

    @Autowired
    public ChatService(AlertRecordMapper alertRecordMapper, RagClient ragClient) {
        this.alertRecordMapper = alertRecordMapper;
        this.ragClient = ragClient;
    }

    ChatService(AlertRecordMapper alertRecordMapper) {
        this(alertRecordMapper, null);
    }

    public ChatResponse answer(String question, Long alertId) {
        AlertRecord alert = alertId == null ? null : alertRecordMapper.selectById(alertId);
        if (ragClient != null) {
            var answer = ragClient.query(question, alert);
            if (answer.isPresent()) {
                var ragAnswer = answer.get();
                return new ChatResponse(
                        ragAnswer.answer(),
                        ragAnswer.source(),
                        ragAnswer.model(),
                        ragAnswer.riskLevel(),
                        ragAnswer.summary(),
                        ragAnswer.immediateActions(),
                        ragAnswer.verificationSteps(),
                        ragAnswer.escalationConditions(),
                        ragAnswer.safetyNotice(),
                        ragAnswer.sources().stream()
                                .map(source -> new ChatResponse.KnowledgeSource(source.id(), source.title()))
                                .toList()
                );
            }
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        if (normalized.contains("疏散") || normalized.contains("火情")) {
            return response("发现火情或高风险烟雾时，请立即通知人员沿安全通道有序疏散，不乘坐电梯，并拨打 119。确认现场安全后再进行告警处置。", "HIGH");
        }
        if (normalized.contains("离线") || normalized.contains("心跳")) {
            return response("设备离线时，请检查电源、网络与设备心跳。设备恢复上报后，系统会自动关闭对应的离线告警。", "MEDIUM");
        }
        if (normalized.contains("阈值")) {
            return response("可在“设备管理”中编辑设备并设置阈值。阈值必须是大于 0 的整数 ppm；调整后应结合现场环境进行验证。", "LOW");
        }
        if (alert != null) {
            String type = alert.getAlertType() == AlertRecord.TYPE_SMOKE ? "烟雾" : "离线";
            return response("当前关联的是设备 " + alert.getDeviceId() + " 的" + type
                    + "告警。请先确认告警，再完成处置；若经现场核验为非火情，可标记为误报。",
                    alert.getAlertType() == AlertRecord.TYPE_SMOKE ? "HIGH" : "MEDIUM");
        }
        return response("我可以协助说明告警确认与处置、人员疏散、设备离线处理和阈值设置。你也可以先在监控大屏中选择有告警的设备再提问。", "UNKNOWN");
    }

    private ChatResponse response(String answer, String riskLevel) {
        return ChatResponse.plain(answer, "SYSTEM_KNOWLEDGE_BASE", riskLevel);
    }
}
