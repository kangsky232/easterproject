package com.smoke.dto;

/**
 * 设备接入令牌只在绑定或轮换时返回一次，服务端仅保存其摘要。
 */
public record DeviceCredentialResponse(String deviceId, String accessToken) {

    @Override
    public String toString() {
        return "DeviceCredentialResponse[deviceId=" + deviceId + ", accessToken=[REDACTED]]";
    }
}
