package com.smoke.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.dingtalk")
public class DingTalkProperties {

    private boolean enabled;
    private String clientId = "";
    private String clientSecret = "";
    private String robotCode = "";

    public boolean isConfigured() {
        return enabled && hasText(clientId) && hasText(clientSecret);
    }

    public String resolvedRobotCode() {
        return hasText(robotCode) ? robotCode.trim() : clientId.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
