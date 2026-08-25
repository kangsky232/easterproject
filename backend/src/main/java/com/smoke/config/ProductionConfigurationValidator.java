package com.smoke.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生产配置不完整时拒绝启动，避免开发默认值意外暴露到公网。
 */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionConfigurationValidator implements ApplicationRunner {

    private static final String DEVELOPMENT_JWT_SECRET = "change-me-to-a-long-random-secret-32-bytes";

    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();
        String jwtSecret = value("jwt.secret");
        require(problems, jwtSecret.length() >= 32 && !DEVELOPMENT_JWT_SECRET.equals(jwtSecret) && configured(jwtSecret),
                "JWT_SECRET 必须设置为不少于 32 字节的随机值");
        require(problems, configured(value("spring.datasource.password")), "DB_PASSWORD 不能为空或示例值");
        require(problems, configured(value("spring.datasource.username"))
                        && !"root".equalsIgnoreCase(value("spring.datasource.username")),
                "DB_USERNAME 不能使用 root");
        require(problems, Boolean.parseBoolean(value("app.device-auth.enabled")),
                "生产环境必须启用 DEVICE_AUTH_ENABLED");

        List<String> origins = Arrays.stream(value("app.cors.allowed-origins").split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        require(problems, !origins.isEmpty()
                        && origins.stream().allMatch(origin -> origin.startsWith("https://")
                        && !origin.contains("localhost") && !origin.contains("127.0.0.1")
                        && !origin.contains("example.com")),
                "CORS_ALLOWED_ORIGINS 必须是正式 HTTPS 前端域名");
        require(problems, !Boolean.parseBoolean(value("springdoc.swagger-ui.enabled"))
                        && !Boolean.parseBoolean(value("springdoc.api-docs.enabled")),
                "生产环境必须关闭 Swagger 与 OpenAPI 文档端点");

        boolean bootstrapEnabled = Boolean.parseBoolean(value("bootstrap-admin.enabled"));
        if (bootstrapEnabled) {
            String password = value("bootstrap-admin.password");
            require(problems, password.length() >= 12 && !"admin123".equals(password) && configured(password),
                    "启用初始管理员时，BOOTSTRAP_ADMIN_PASSWORD 必须为至少 12 位的非默认密码");
        }
        require(problems, !Boolean.parseBoolean(value("bootstrap-admin.reset-password")),
                "生产环境不允许启用 BOOTSTRAP_ADMIN_RESET_PASSWORD");
        if (!problems.isEmpty()) {
            throw new IllegalStateException("生产配置校验失败：" + String.join("；", problems));
        }
    }

    private String value(String key) {
        return environment.getProperty(key, "");
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank() && !value.startsWith("REPLACE_");
    }

    private void require(List<String> problems, boolean condition, String message) {
        if (!condition) {
            problems.add(message);
        }
    }
}
