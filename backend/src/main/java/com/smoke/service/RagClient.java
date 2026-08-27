package com.smoke.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoke.entity.AlertRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RagClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Value("${rag.service-url:http://127.0.0.1:5001/api/chat/query}")
    private String serviceUrl;

    @Value("${rag.health-url:http://127.0.0.1:5001/api/health}")
    private String healthUrl;

    @Value("${rag.timeout-seconds:130}")
    private long timeoutSeconds;

    @Value("${rag.health-timeout-seconds:2}")
    private long healthTimeoutSeconds;

    public RagHealth health() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(healthTimeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return RagHealth.unavailable();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            boolean available = root.path("code").asInt(-1) == 0
                    && "UP".equalsIgnoreCase(data.path("status").asText(""));
            if (!available) {
                return RagHealth.unavailable();
            }
            return new RagHealth(
                    true,
                    data.path("llmProvider").asText(""),
                    data.path("llmModel").asText(""));
        } catch (IOException exception) {
            return RagHealth.unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RagHealth.unavailable();
        } catch (RuntimeException exception) {
            return RagHealth.unavailable();
        }
    }

    public Optional<RagAnswer> query(String question, AlertRecord alert) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("question", question);
            if (alert != null) {
                payload.put("alert", alertContext(alert));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(serviceUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            String answer = data.path("answer").asText("").trim();
            if (root.path("code").asInt(-1) != 0 || answer.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RagAnswer(
                    answer,
                    data.path("source").asText("RAG"),
                    data.path("model").asText(""),
                    data.path("riskLevel").asText("UNKNOWN"),
                    data.path("summary").asText(answer),
                    stringList(data.path("immediateActions"), 5),
                    stringList(data.path("verificationSteps"), 5),
                    stringList(data.path("escalationConditions"), 4),
                    data.path("safetyNotice").asText(""),
                    sources(data.path("sources"))
            ));
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> alertContext(AlertRecord alert) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("deviceId", alert.getDeviceId());
        context.put("alertType", AlertRecord.typeLabel(alert.getAlertType()));
        context.put("concentration", alert.getConcentration());
        context.put("threshold", alert.getThreshold());
        context.put("severity", alert.getSeverity());
        context.put("ruleDescription", alert.getRuleDescription());
        context.put("status", alert.getStatus());
        return context;
    }

    private List<String> stringList(JsonNode node, int limit) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
            if (values.size() >= limit) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private List<RagSource> sources(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<RagSource> values = new ArrayList<>();
        for (JsonNode item : node) {
            String id = item.path("id").asText("").trim();
            String title = item.path("title").asText("").trim();
            if (!id.isEmpty() || !title.isEmpty()) {
                values.add(new RagSource(id, title));
            }
            if (values.size() >= 5) {
                break;
            }
        }
        return List.copyOf(values);
    }

    public record RagAnswer(
            String answer,
            String source,
            String model,
            String riskLevel,
            String summary,
            List<String> immediateActions,
            List<String> verificationSteps,
            List<String> escalationConditions,
            String safetyNotice,
            List<RagSource> sources
    ) {
    }

    public record RagSource(String id, String title) {
    }

    public record RagHealth(boolean available, String provider, String model) {
        public static RagHealth unavailable() {
            return new RagHealth(false, "", "");
        }
    }
}
