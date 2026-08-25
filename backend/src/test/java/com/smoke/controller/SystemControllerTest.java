package com.smoke.controller;

import com.smoke.mqtt.HuaweiMqttSubscriber;
import com.smoke.service.RagClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemControllerTest {

    @Test
    void healthReportsUpOnlyAfterDatabaseProbeSucceeds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        SystemController controller = controller(jdbcTemplate, RagClient.RagHealth.unavailable(), false);

        var response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().getCode());
    }

    @Test
    void healthReportsServiceUnavailableWhenDatabaseProbeFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("database unavailable"));
        SystemController controller = controller(jdbcTemplate, RagClient.RagHealth.unavailable(), false);

        var response = controller.health();

        assertEquals(503, response.getStatusCode().value());
        assertEquals(503, response.getBody().getCode());
    }

    @Test
    void capabilitiesReportConnectedKnowledgeBaseFromLiveHealth() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemController controller = controller(
                jdbcTemplate, new RagClient.RagHealth(true, "OLLAMA", "gpt-oss:120b-cloud"), false);

        var response = controller.capabilities();
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.getData();

        assertEquals("CONNECTED", data.get("knowledgeBase"));
        assertEquals("OLLAMA", data.get("llmProvider"));
        assertEquals("gpt-oss:120b-cloud", data.get("llmModel"));
        assertEquals("LOCAL_DEVELOPMENT", data.get("mode"));
    }

    @Test
    void capabilitiesExposeFallbackWhenKnowledgeBaseIsUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemController controller = controller(jdbcTemplate, RagClient.RagHealth.unavailable(), true);

        var response = controller.capabilities();
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.getData();

        assertEquals("FALLBACK_ONLY", data.get("knowledgeBase"));
        assertEquals("PRODUCTION", data.get("mode"));
        assertTrue(((String) data.get("llmModel")).isEmpty());
    }

    private SystemController controller(
            JdbcTemplate jdbcTemplate, RagClient.RagHealth health, boolean production) {
        RagClient ragClient = mock(RagClient.class);
        Environment environment = mock(Environment.class);
        HuaweiMqttSubscriber mqttSubscriber = mock(HuaweiMqttSubscriber.class);
        when(ragClient.health()).thenReturn(health);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(production);
        return new SystemController(jdbcTemplate, ragClient, environment, mqttSubscriber);
    }
}
