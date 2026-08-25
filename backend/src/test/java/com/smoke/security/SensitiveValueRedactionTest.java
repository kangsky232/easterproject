package com.smoke.security;

import com.smoke.dto.ChangePasswordRequest;
import com.smoke.dto.CreateUserRequest;
import com.smoke.dto.DeviceCredentialResponse;
import com.smoke.dto.LoginRequest;
import com.smoke.dto.LoginResponse;
import com.smoke.dto.ResetPasswordRequest;
import com.smoke.entity.Device;
import com.smoke.entity.UserAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveValueRedactionTest {

    @Test
    void requestAndResponseLogsDoNotExposeSecrets() {
        assertRedacted(new LoginRequest("admin", "login-secret"), "login-secret");
        assertRedacted(new LoginResponse("jwt-secret", "Bearer", null), "jwt-secret");
        assertRedacted(new ChangePasswordRequest("old-secret", "new-secret"), "old-secret", "new-secret");
        assertRedacted(
                new CreateUserRequest("operator", "create-secret", "Operator", "COMMUNITY_ADMIN", "13800000000"),
                "create-secret",
                "13800000000");
        assertRedacted(new ResetPasswordRequest("reset-secret"), "reset-secret");
        assertRedacted(new DeviceCredentialResponse("SMOKE-001", "device-secret"), "device-secret");
    }

    @Test
    void entityLogsDoNotExposeStoredCredentialMaterial() {
        UserAccount user = new UserAccount();
        user.setUsername("admin");
        user.setPasswordHash("password-hash-secret");
        assertRedacted(user, "password-hash-secret");

        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setDeviceTokenHash("device-hash-secret");
        device.setDeviceAccessToken("device-access-secret");
        assertRedacted(device, "device-hash-secret", "device-access-secret");
    }

    private void assertRedacted(Object value, String... secrets) {
        String logValue = value.toString();
        for (String secret : secrets) {
            assertFalse(logValue.contains(secret));
        }
    }
}
