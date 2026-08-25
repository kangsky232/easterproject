package com.smoke.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoke.dto.TelemetryRequest;
import com.smoke.service.TelemetryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class HuaweiMqttSubscriber implements MqttCallbackExtended {

    private static final Pattern DEVICE_ID_FROM_TOPIC = Pattern.compile("^\\$oc/devices/([^/]+)/");
    private static final int QOS = 1;

    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.enabled:false}")
    private boolean enabled;

    @Value("${mqtt.broker:}")
    private String broker;

    @Value("${mqtt.access-key:}")
    private String accessKey;

    @Value("${mqtt.access-code:}")
    private String accessCode;

    @Value("${mqtt.instance-id:}")
    private String instanceId;

    @Value("${mqtt.topic:smoke/report}")
    private String topic;

    private volatile MqttClient client;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("MQTT 数据接入已禁用");
            return;
        }
        connect();
    }

    @Scheduled(fixedDelayString = "${mqtt.reconnect-interval-ms:30000}")
    public void ensureConnected() {
        if (enabled && (client == null || !client.isConnected())) {
            connect();
        }
    }

    private synchronized void connect() {
        if (client != null && client.isConnected()) {
            return;
        }
        if (isBlank(broker) || isBlank(accessKey) || isBlank(accessCode)) {
            log.warn("MQTT 配置不完整（broker/access-key/access-code），跳过连接");
            return;
        }
        MqttClient newClient = null;
        try {
            String credential = buildCredential();
            newClient = new MqttClient(broker, credential);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(credential);
            options.setPassword(accessCode.toCharArray());
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            newClient.setCallback(this);
            newClient.connect(options);
            newClient.subscribe(topic, QOS);
            this.client = newClient;
            log.info("MQTT 已连接: {}，订阅主题 {}", broker, topic);
        } catch (MqttException exception) {
            log.warn("MQTT 连接失败: {}（将在下次重试）", exception.getMessage());
            closeQuietly(newClient);
        }
    }

    private String buildCredential() {
        StringBuilder credential = new StringBuilder("accessKey=").append(accessKey)
                .append("|timestamp=").append(System.currentTimeMillis());
        if (!isBlank(instanceId)) {
            credential.append("|instanceId=").append(instanceId);
        }
        return credential.toString();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT 连接完成，reconnect={}", reconnect);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接断开: {}", cause == null ? "未知原因" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String deviceId = resolveDeviceId(topic, root);
            Integer concentration = extractConcentration(root);
            if (deviceId == null || concentration == null) {
                log.warn("忽略无法解析的 MQTT 消息: topic={}, payload={}", topic,
                        new String(message.getPayload(), StandardCharsets.UTF_8));
                return;
            }
            telemetryService.record(new TelemetryRequest(
                    deviceId,
                    concentration,
                    deviceId + ":" + System.currentTimeMillis(),
                    LocalDateTime.now()));
        } catch (Exception exception) {
            log.warn("处理 MQTT 消息失败: topic={}", topic, exception);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    private String resolveDeviceId(String topic, JsonNode root) {
        for (String pointer : List.of("/devices/0/device_id", "/notify_data/header/device_id", "/device_id")) {
            JsonNode value = root.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asText();
            }
        }
        Matcher matcher = DEVICE_ID_FROM_TOPIC.matcher(topic);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer extractConcentration(JsonNode root) {
        for (String pointer : List.of("/devices/0/services", "/notify_data/body/services", "/services")) {
            JsonNode services = root.at(pointer);
            if (services.isMissingNode() || services.isNull() || !services.isArray() || services.isEmpty()) {
                continue;
            }
            JsonNode value = services.get(0).at("/properties/Smoke_Value");
            if (!value.isMissingNode() && !value.isNull()) {
                // Smoke_Value 是浮点，直接取整（截断）映射到整数 concentration
                return (int) value.asDouble();
            }
        }
        return null;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @PreDestroy
    public void stop() {
        closeQuietly(client);
    }

    private void closeQuietly(MqttClient target) {
        if (target == null) {
            return;
        }
        try {
            target.disconnect();
        } catch (MqttException ignored) {
        }
        try {
            target.close();
        } catch (MqttException ignored) {
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
