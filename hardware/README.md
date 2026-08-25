# 设备与硬件接入说明

更新日期：2026-08-24。

当前仓库尚未包含可直接烧录的 ESP8266、STM32 或小熊派固件。后端已经实现 HTTP 心跳、遥测和设备令牌校验；MQTT 主题仅是后续适配器约定，当前后端不会订阅或发布这些主题。

## 当前可用：HTTP 接入

设备由管理员通过 `POST /api/devices/bind` 绑定。生产环境响应中的 `deviceAccessToken` 只显示一次，之后每次上报都必须放在请求头中。

遥测：

```http
POST /api/telemetry
X-Device-Token: <deviceAccessToken>
Content-Type: application/json

{
  "deviceId": "SMOKE-001",
  "concentration": 850,
  "messageId": "SMOKE-001-20260824-0001",
  "timestamp": "2026-08-24T15:00:00"
}
```

心跳：

```http
POST /api/heartbeat
X-Device-Token: <deviceAccessToken>
Content-Type: application/json

{"deviceId":"SMOKE-001","battery":86}
```

- `messageId` 用于幂等；设备重试必须复用原值。
- `battery` 范围为 `0`–`100`，可不传。
- 开发配置默认可关闭设备令牌校验，生产配置强制开启。
- 令牌遗失或泄露时，管理员调用 `POST /api/devices/{id}/credentials` 轮换。

## 规划中的 MQTT 约定

只有在后端实现 MQTT 订阅/发布适配器并启用 Broker 认证后，以下主题才可使用：

| 方向 | 主题 | 预期用途 |
| --- | --- | --- |
| 设备 → 平台 | `/device/{deviceId}/telemetry` | 烟雾浓度数据 |
| 设备 → 平台 | `/device/{deviceId}/heartbeat` | 电量与在线心跳 |
| 设备 → 平台 | `/device/{deviceId}/status` | 状态与指令回执 |
| 平台 → 设备 | `/platform/command/{deviceId}` | 广播等指令 |
| 平台 → 设备 | `/platform/config/{deviceId}` | 阈值等配置 |

接入时必须进一步确定 JSON payload、QoS、保留消息、离线队列、重放幂等和回执超时，不能只依赖主题名称。

## 安全要求

- 每台设备使用独立凭据，禁止所有设备共享一个明文密码。
- Broker 启用 TLS、账号认证和按设备主题限制的 ACL。
- 设备令牌、Wi-Fi 密码和 Broker 凭据不得写入公共固件仓库或串口日志。
- 生产设备要支持凭据轮换、时钟校准、断网缓存和指数退避重试。
