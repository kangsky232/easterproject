package com.smoke.controller;

import com.smoke.common.Result;
import com.smoke.mqtt.HuaweiMqttSubscriber;
import com.smoke.service.RagClient;
import com.smoke.service.DingTalkMessageService;
import com.smoke.service.VisionPatrolService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final JdbcTemplate jdbcTemplate;
    private final RagClient ragClient;
    private final Environment environment;
    private final HuaweiMqttSubscriber huaweiMqttSubscriber;
    private final DingTalkMessageService dingTalkMessageService;
    private final VisionPatrolService visionPatrolService;

    @GetMapping("/health")
    public ResponseEntity<Result<?>> health() {
        try {
            Integer databaseProbe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (!Integer.valueOf(1).equals(databaseProbe)) {
                throw new IllegalStateException("数据库探测结果异常");
            }
            return ResponseEntity.ok(Result.ok(Map.of(
                    "status", "UP",
                    "database", "UP",
                    "time", LocalDateTime.now(),
                    "storage", "MYSQL",
                    "deviceTransport", "REST")));
        } catch (Exception exception) {
            return ResponseEntity.status(503).body(Result.fail(503, "数据库不可用"));
        }
    }

    @GetMapping("/system/capabilities")
    public Result<?> capabilities() {
        RagClient.RagHealth ragHealth = ragClient.health();
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("mode", environment.acceptsProfiles(Profiles.of("prod"))
                ? "PRODUCTION" : "LOCAL_DEVELOPMENT");
        capabilities.put("storage", "MYSQL");
        capabilities.put("deviceIngress", "REST");
        capabilities.put("mqtt", huaweiMqttSubscriber.isConnected() ? "CONNECTED" : "NOT_CONNECTED");
        capabilities.put("visualAi", visionPatrolService.capability());
        capabilities.put("knowledgeBase", ragHealth.available() ? "CONNECTED" : "FALLBACK_ONLY");
        capabilities.put("llmProvider", ragHealth.provider());
        capabilities.put("llmModel", ragHealth.model());
        capabilities.put("broadcast", dingTalkMessageService.isConfigured()
                ? "DINGTALK_SINGLE_CHAT" : "PERSISTENCE_ONLY");
        return Result.ok(capabilities);
    }
}
