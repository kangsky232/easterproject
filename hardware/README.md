# 设备与硬件接入说明

更新日期：2026-08-26。

当前仓库尚未包含可直接烧录的 ESP8266、STM32 或小熊派固件。后端支持两条入站链路：设备直接调用 HTTP 接口，以及订阅华为云 IoTDA 规则引擎转发到 MQTT Broker 的消息。MQTT 入站已经实现，MQTT 广播下发尚未实现。

## 当前可用：HTTP 接入

设备由管理员通过 `POST /api/devices/bind` 绑定。生产环境响应中的 `deviceAccessToken` 只显示一次，之后每次上报都必须放在请求头中。

遥测：

```http
POST /api/telemetry
X-Device-Token: <deviceAccessToken>
Content-Type: application/json

{
  "deviceId": "SMOKE-001",
  "concentration": 20.37,
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
- `concentration` 接受 JSON 数字，范围为 `0`–`1000000`；后端按四舍五入保留两位小数。
- `battery` 范围为 `0`–`100`，可不传。
- 开发配置默认可关闭设备令牌校验，生产配置强制开启。
- 令牌遗失或泄露时，管理员调用 `POST /api/devices/{id}/credentials` 轮换。

## 当前可用：华为云 IoTDA MQTT 入站

IoTDA 规则引擎把设备属性消息转发到 MQTT 后，Spring Boot 使用以下变量连接并订阅：

| 变量 | 说明 |
| --- | --- |
| `MQTT_ENABLED` | 设置为 `true` 才启动订阅器 |
| `MQTT_BROKER` | TLS Broker 地址，例如 `ssl://host:8883` |
| `MQTT_ACCESS_KEY` | IoTDA 接入信息中的 Access Key |
| `MQTT_ACCESS_CODE` | IoTDA 接入密码/Access Code |
| `MQTT_INSTANCE_ID` | MQTT 实例 ID；平台未要求时可留空，填写后会加入登录凭据 |
| `MQTT_TOPIC` | 规则引擎转发到的订阅主题，默认 `smoke/report` |

支持的典型 payload：

```json
{
  "devices": [
    {
      "device_id": "SMOKE-001",
      "services": [
        {
          "service_id": "Smoke",
          "properties": {"Smoke_Value": 20.37}
        }
      ]
    }
  ]
}
```

后端也兼容 IoTDA 通知结构中的 `notify_data.header.device_id`、`notify_data.body.services`，以及顶层 `device_id`、`services`。如果 topic 使用 `$oc/devices/{deviceId}/...`，还可从 topic 提取设备编号。设备必须已经在平台绑定，否则消息会被忽略。

`Smoke_Value` 必须是 JSON 数字。后端会保留两位小数并将遥测同时作为设备在线心跳。`GET /api/system/capabilities` 中的 `mqtt=CONNECTED` 只说明后端已连接 Broker，不代表硬件正在上报；是否在线应查看设备的 `lastHeartbeat` 和最新数据时间。

旧版本曾把 `Smoke_Value` 强制转换为整数，因此历史记录中已经丢失的小数无法恢复。升级已有数据库时执行 `docs/migrations/20260826_decimal_concentration.sql`。

## 尚未实现：MQTT 下行

广播记录目前只写入数据库，不会通过 MQTT 发布给设备。正式下行还需定义命令主题、QoS、保留消息、离线队列、重放幂等、设备回执和超时策略。

## 安全要求

- 每台设备使用独立凭据，禁止所有设备共享一个明文密码。
- Broker 启用 TLS、账号认证和按设备主题限制的 ACL。
- 设备令牌、Wi-Fi 密码和 Broker 凭据不得写入公共固件仓库或串口日志。
- 生产设备要支持凭据轮换、时钟校准、断网缓存和指数退避重试。
