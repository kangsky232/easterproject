package com.smoke.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vision")
public class VisionProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-flash-vision-exp";
    private String frameBaseUrl = "https://easterproject.pages.dev";
    private long intervalMs = 15000L;
    private long initialDelayMs = 3000L;
    private int timeoutSeconds = 45;
    private double confidenceThreshold = 0.65D;

    public boolean isDeepSeekConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
