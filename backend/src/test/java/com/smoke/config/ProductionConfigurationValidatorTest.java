package com.smoke.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsCompleteProductionConfiguration() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(validEnvironment());

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsDevelopmentJwtSecret() {
        MockEnvironment environment = validEnvironment()
                .withProperty("jwt.secret", "change-me-to-a-long-random-secret-32-bytes");
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(environment);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsBootstrapPasswordRecoveryInProduction() {
        MockEnvironment environment = validEnvironment()
                .withProperty("bootstrap-admin.reset-password", "true");
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(environment);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("jwt.secret", "a-production-secret-with-at-least-32-characters")
                .withProperty("spring.datasource.password", "a-long-database-password")
                .withProperty("spring.datasource.username", "smart_smoke_app")
                .withProperty("app.device-auth.enabled", "true")
                .withProperty("app.cors.allowed-origins", "https://smoke.company.cn")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("bootstrap-admin.enabled", "false");
    }
}
