package com.smoke.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoke.config.VisionProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekVisionClientTest {

    @Test
    void usesClearlyLabelledSimulationWhenApiKeyIsMissing() {
        VisionProperties properties = new VisionProperties();
        DeepSeekVisionClient client = new DeepSeekVisionClient(properties, new ObjectMapper());
        SimulatedVisionFrame frame = new SimulatedVisionFrame(
                "smoke-frame", "A1-05F-C02", "1号楼5层西侧过道", "A1", 5,
                "https://example.com/smoke.jpg", true,
                "发现疑似烟雾", "灰白色烟雾区域");

        VisionAnalysisResult result = client.analyze(frame);

        assertTrue(result.suspectedFire());
        assertEquals(0.92D, result.confidence());
        assertEquals("SIMULATION_FALLBACK", result.mode());
        assertEquals("built-in-scenario-rules", result.model());
        assertNull(result.error());
    }
}
