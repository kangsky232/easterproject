package com.smoke.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.entity.Device;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 校验设备遥测和心跳请求携带的独立设备令牌，避免使用管理端 JWT 作为设备凭据。
 */
@Service
@RequiredArgsConstructor
public class DeviceRequestAuthentication {

    private final DeviceMapper deviceMapper;

    @Value("${app.device-auth.enabled:false}")
    private boolean enabled;

    public void verify(String deviceId, String accessToken) {
        if (!enabled) {
            return;
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(401, "设备请求缺少 X-Device-Token");
        }
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, deviceId)
                .eq(Device::getBound, 1));
        if (device == null || !DeviceCredentialCodec.matches(accessToken, device.getDeviceTokenHash())) {
            throw new BusinessException(401, "设备身份校验失败");
        }
    }
}
