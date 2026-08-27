package com.smoke.dingtalk;

import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.smoke.mapper.DingTalkRecipientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.dingtalk", name = "enabled", havingValue = "true")
public class DingTalkBotMessageHandler implements OpenDingTalkCallbackListener<ChatbotMessage, Void> {

    private final DingTalkRecipientMapper recipientMapper;

    @Override
    public Void execute(ChatbotMessage message) {
        String userId = message.getSenderStaffId();
        String response;
        if (userId == null || userId.isBlank()) {
            response = "消息已收到，但无法识别企业员工 userId，暂时不能绑定广播接收。";
            log.warn("DingTalk bot message has no senderStaffId");
        } else {
            recipientMapper.upsertActiveRecipient(userId.trim(), trimmed(message.getSenderNick()));
            response = "连接成功。您已绑定智慧烟感广播，后续广播会发送到这个钉钉单聊。";
            log.info("DingTalk recipient bound: {}", userId);
        }
        try {
            BotReplier.fromWebhook(message.getSessionWebhook()).replyText(response);
        } catch (IOException exception) {
            throw new IllegalStateException("回复钉钉机器人消息失败", exception);
        }
        return null;
    }

    private String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }
}
