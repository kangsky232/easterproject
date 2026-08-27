package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoke.config.DingTalkProperties;
import com.smoke.entity.DingTalkRecipient;
import com.smoke.mapper.DingTalkRecipientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DingTalkMessageService {

    private static final String ACCESS_TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
    private static final String BATCH_SEND_URL = "https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend";
    private static final int RECIPIENT_BATCH_SIZE = 100;

    private final DingTalkProperties properties;
    private final DingTalkRecipientMapper recipientMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public DingTalkMessageService(DingTalkProperties properties,
                                  DingTalkRecipientMapper recipientMapper,
                                  ObjectMapper objectMapper,
                                  RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.recipientMapper = recipientMapper;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public int sendBroadcast(String deviceId, String content) {
        if (!properties.isConfigured()) {
            throw new DingTalkDeliveryException("钉钉广播尚未配置");
        }

        List<String> userIds = recipientMapper.selectList(Wrappers.<DingTalkRecipient>lambdaQuery()
                        .eq(DingTalkRecipient::getEnabled, 1)
                        .orderByAsc(DingTalkRecipient::getId))
                .stream()
                .map(DingTalkRecipient::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new DingTalkDeliveryException("还没有已绑定的钉钉用户，请先在钉钉中私聊机器人");
        }

        String text = "【智慧烟感广播】\n设备：" + deviceId + "\n" + content;
        for (int offset = 0; offset < userIds.size(); offset += RECIPIENT_BATCH_SIZE) {
            int end = Math.min(offset + RECIPIENT_BATCH_SIZE, userIds.size());
            sendBatch(userIds.subList(offset, end), text);
        }
        log.info("DingTalk broadcast delivered to {} recipient(s) for device {}", userIds.size(), deviceId);
        return userIds.size();
    }

    private void sendBatch(List<String> userIds, String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("robotCode", properties.resolvedRobotCode());
        request.put("userIds", new ArrayList<>(userIds));
        request.put("msgKey", "sampleText");
        request.put("msgParam", json(Map.of("content", text)));

        try {
            restClient.post()
                    .uri(BATCH_SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-acs-dingtalk-access-token", accessToken())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw apiFailure("发送单聊消息", exception);
        } catch (DingTalkDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DingTalkDeliveryException("钉钉发送请求失败：" + safeMessage(exception), exception);
        }
    }

    private synchronized String accessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }
        Map<String, String> request = Map.of(
                "appKey", properties.getClientId().trim(),
                "appSecret", properties.getClientSecret().trim());
        try {
            Map<String, Object> response = restClient.post()
                    .uri(ACCESS_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            String token = response == null ? null : String.valueOf(response.get("accessToken"));
            if (token == null || token.isBlank() || "null".equals(token)) {
                throw new DingTalkDeliveryException("钉钉未返回 accessToken");
            }
            long expireIn = number(response.get("expireIn"), 7200L);
            cachedAccessToken = token;
            accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60L, expireIn - 120L));
            return token;
        } catch (RestClientResponseException exception) {
            throw apiFailure("获取 accessToken", exception);
        } catch (DingTalkDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DingTalkDeliveryException("钉钉鉴权请求失败：" + safeMessage(exception), exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DingTalkDeliveryException("无法生成钉钉消息内容", exception);
        }
    }

    private long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private DingTalkDeliveryException apiFailure(String action, RestClientResponseException exception) {
        String response = exception.getResponseBodyAsString();
        if (response.length() > 500) {
            response = response.substring(0, 500);
        }
        return new DingTalkDeliveryException(
                "钉钉" + action + "失败（HTTP " + exception.getStatusCode().value() + "）：" + response,
                exception);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public static class DingTalkDeliveryException extends RuntimeException {

        public DingTalkDeliveryException(String message) {
            super(message);
        }

        public DingTalkDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
