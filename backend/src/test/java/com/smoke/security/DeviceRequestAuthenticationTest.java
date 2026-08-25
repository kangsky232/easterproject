package com.smoke.security;

import com.smoke.entity.Device;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRequestAuthenticationTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Test
    void requiresAValidDeviceTokenWhenEnabled() {
        Device device = new Device();
        device.setDeviceId("SMOKE-001");
        device.setBound(1);
        device.setDeviceTokenHash(DeviceCredentialCodec.hash("device-secret"));
        when(deviceMapper.selectOne(any())).thenReturn(device);

        DeviceRequestAuthentication authentication = new DeviceRequestAuthentication(deviceMapper);
        ReflectionTestUtils.setField(authentication, "enabled", true);

        assertDoesNotThrow(() -> authentication.verify("SMOKE-001", "device-secret"));
        assertThrows(BusinessException.class, () -> authentication.verify("SMOKE-001", "wrong-secret"));
        assertThrows(BusinessException.class, () -> authentication.verify("SMOKE-001", null));
    }
}
