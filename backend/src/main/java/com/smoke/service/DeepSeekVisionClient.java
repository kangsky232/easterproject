package com.smoke.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoke.config.VisionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DeepSeekVisionClient {

    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final VisionProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekVisionClient(VisionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1000, properties.getTimeoutSeconds() * 1000);
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isConfigured() {
        return properties.isDeepSeekConfigured();
    }

    public VisionAnalysisResult analyze(SimulatedVisionFrame frame) {
        if (!properties.isDeepSeekConfigured()) {
            return simulationFallback(frame);
        }
        try {
            Map<String, Object> imageUrl = Map.of("url", frame.imageUrl(), "detail", "low");
            List<Map<String, Object>> userContent = List.of(
                    Map.of("type", "text", "text", prompt(frame)),
                    Map.of("type", "image_url", "image_url", imageUrl));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", properties.getModel());
            request.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt()),
                    Map.of("role", "user", "content", userContent)));
            request.put("temperature", 0);
            request.put("max_tokens", 350);
            request.put("response_format", Map.of("type", "json_object"));

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null
                    ? ""
                    : response.path("choices").path(0).path("message").path("content").asText("");
            return parse(content);
        } catch (RestClientResponseException exception) {
            String message = "DeepSeek Vision HTTP " + exception.getStatusCode().value();
            log.warn("{} while analyzing frame {}", message, frame.frameKey());
            return error(message);
        } catch (Exception exception) {
            String message = safeMessage(exception);
            log.warn("DeepSeek Vision failed for frame {}: {}", frame.frameKey(), message);
            return error(message);
        }
    }

    private VisionAnalysisResult simulationFallback(SimulatedVisionFrame frame) {
        boolean suspicious = frame.expectedSuspicious();
        return new VisionAnalysisResult(
                suspicious,
                suspicious ? 0.92D : 0.08D,
                suspicious ? "HIGH" : "LOW",
                frame.fallbackSummary(),
                frame.fallbackEvidence(),
                "SIMULATION_FALLBACK",
                "built-in-scenario-rules",
                null);
    }

    private VisionAnalysisResult parse(String content) throws Exception {
        String normalized = stripCodeFence(content);
        if (normalized.isBlank()) {
            throw new IllegalStateException("DeepSeek 未返回分析内容");
        }
        JsonNode result = objectMapper.readTree(normalized);
        boolean suspected = result.path("suspectedFire").asBoolean(false);
        double confidence = clamp(result.path("confidence").asDouble(0D));
        String riskLevel = result.path("riskLevel").asText(suspected ? "HIGH" : "LOW")
                .trim().toUpperCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(riskLevel)) {
            riskLevel = suspected ? "HIGH" : "LOW";
        }
        String summary = text(result, "summary", suspected ? "发现疑似火灾迹象" : "未发现明显火灾迹象");
        String evidence = evidence(result.path("evidence"));
        return new VisionAnalysisResult(
                suspected,
                confidence,
                riskLevel,
                limit(summary, 500),
                limit(evidence, 1000),
                "DEEPSEEK_VISION",
                properties.getModel(),
                null);
    }

    private VisionAnalysisResult error(String message) {
        return new VisionAnalysisResult(
                false, 0D, "UNKNOWN", "本帧 AI 分析失败", "未形成视觉判断",
                "DEEPSEEK_ERROR", properties.getModel(), limit(message, 500));
    }

    private String systemPrompt() {
        return "你是消防监控图片复核模型。只分析画面中可见证据，不推测画面外事实。"
                + "重点识别明火、浓烟、烟雾扩散、电气起火迹象，并区分灯光、雾气、反光。"
                + "必须输出单个 JSON 对象，不要 Markdown："
                + "{\"suspectedFire\":boolean,\"confidence\":0到1数字,"
                + "\"riskLevel\":\"LOW|MEDIUM|HIGH|CRITICAL\","
                + "\"summary\":\"简短结论\",\"evidence\":[\"可见依据\"]}。"
                + "不确定时降低置信度；模型结论必须由人工复核。";
    }

    private String prompt(SimulatedVisionFrame frame) {
        return "分析这张社区监控图片是否存在疑似火灾。机位：" + frame.cameraCode()
                + "，位置：" + frame.location() + "。请按系统要求返回 JSON。";
    }

    private String evidence(JsonNode node) {
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    if (!builder.isEmpty()) builder.append('；');
                    builder.append(value);
                }
            });
            return builder.isEmpty() ? "模型未提供具体可见依据" : builder.toString();
        }
        String value = node.asText("").trim();
        return value.isBlank() ? "模型未提供具体可见依据" : value;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.isBlank() ? "https://api.deepseek.com" : normalized;
    }

    private String limit(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
