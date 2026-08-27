package com.smoke.dingtalk;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.smoke.config.DingTalkProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("featureSchemaInitializer")
@ConditionalOnProperty(prefix = "app.dingtalk", name = "enabled", havingValue = "true")
public class DingTalkStreamListener {

    private final DingTalkProperties properties;
    private final DingTalkBotMessageHandler messageHandler;

    @PostConstruct
    public void start() throws Exception {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "DINGTALK_ENABLED=true 时必须设置 DINGTALK_CLIENT_ID 和 DINGTALK_CLIENT_SECRET");
        }
        OpenDingTalkClient client = OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(
                        properties.getClientId().trim(),
                        properties.getClientSecret().trim()))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, messageHandler)
                .build();
        client.start();
        log.info("DingTalk Stream listener started");
    }
}
