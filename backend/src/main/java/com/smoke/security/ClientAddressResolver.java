package com.smoke.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 仅当后端被可信反向代理隔离时才读取 X-Forwarded-For，防止客户端伪造来源地址。
 */
@Component
public class ClientAddressResolver {

    @Value("${app.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
