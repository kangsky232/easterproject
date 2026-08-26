# 后端接口

更新日期：2026-08-26。开发环境可通过 `/swagger-ui.html` 查看由代码生成的 OpenAPI 文档；本文件记录业务语义和协作约定。

统一响应格式：

```json
{"code": 0, "message": "success", "data": {}}
```

业务错误通过对应的 HTTP `4xx/5xx` 状态返回，响应体同时包含非零 `code` 和 `message`。

常见状态：`400` 参数错误、`401` 未登录或会话失效、`403` 权限不足、`404` 资源不存在、`409` 状态冲突、`429` 登录尝试过多、`503` 依赖服务不可用。前端必须同时判断 HTTP 状态和响应体 `code`。

除健康检查、系统能力、登录、设备遥测和心跳外，其余接口需要请求头：

```text
Authorization: Bearer <token>
```

全新开发数据库会按 `application-dev.yml` 的引导配置创建管理员；已有数据库不会自动覆盖现有账号密码。团队联调应使用负责人分发的开发账号，生产部署必须通过环境变量设置独立管理员和强 JWT 密钥。

本地开发如遗失引导管理员密码，可在**单次启动**时同时设置 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 和 `BOOTSTRAP_ADMIN_RESET_PASSWORD=true`。该开关默认关闭，生产环境会拒绝启动；重置后必须移除该环境变量。

## 系统

- `GET /api/health`：健康检查，会执行 `SELECT 1` 验证 MySQL；数据库不可用时返回 HTTP `503`。
- `GET /api/system/capabilities`：查询存储、设备接入、MQTT、AI、知识库和广播模块的当前接入状态；知识库状态来自实时 RAG 健康探测，运行模式来自 Spring Profile。
- `GET /api/dashboard/overview`：设备总数、在线数、离线数和活动告警数。

本地前端开发地址 `http://localhost:5173`、`http://127.0.0.1:5173`、`http://localhost:5174` 和 `http://127.0.0.1:5174` 已允许跨域访问 `/api/**`。

## 登录

- `POST /api/auth/login`：登录并获取 JWT。
- `GET /api/auth/me`：查询当前用户。

登录请求：

```json
{"username": "admin", "password": "000000"}
```

角色包括：`RESIDENT`、`COMMUNITY_ADMIN`、`SYSTEM_ADMIN`、`FIREFIGHTER`。

## 用户管理

后端提供独立管理页面：`http://127.0.0.1:8080/admin/`（也可直接访问 `/admin/index.html`）。该页面与主前端分离，使用系统管理员账号登录后可调用以下接口。

以下接口仅系统管理员可用：

- `GET /api/users?page=1&pageSize=20&keyword=&role=&enabled=`：分页查询用户；支持按用户名、显示名称或手机号关键词，以及角色和启用状态筛选。
- `GET /api/users/{id}`：查询单个用户。
- `POST /api/users`：创建用户。
- `PUT /api/users/{id}`：更新显示名称、角色和手机号。
- `PUT /api/users/{id}/status`：启用或禁用用户。
- `PUT /api/users/{id}/password`：重置用户密码。
- `DELETE /api/users/{id}`：永久删除用户。

系统会阻止禁用、删除当前登录账号，修改当前登录账号的角色，或禁用/降级/删除最后一个启用的系统管理员。管理员修改自己的密码时，必须使用下方的本人改密接口。

已登录用户可调用：

- `POST /api/auth/password`：验证当前密码后修改自己的密码。

用户自行改密或被管理员重置密码后，已有 JWT 会立即失效，需使用新密码重新登录。

创建用户：

```json
{
  "username": "security-user",
  "password": "security123",
  "displayName": "安保人员",
  "role": "COMMUNITY_ADMIN",
  "phone": "13800000000"
}
```

账号状态请求：`{"enabled": 0}`；密码重置请求：`{"password": "new-password"}`。

更新用户资料：

```json
{"displayName": "安保负责人", "role": "COMMUNITY_ADMIN", "phone": "13800000000"}
```

本人修改密码：

```json
{"currentPassword": "old-password", "newPassword": "new-password"}
```

## 设备

- `GET /api/devices`：分页查询已绑定设备，并返回每台设备的最新传感器数据。
- `GET /api/devices/{id}`：查询设备资料和最新传感器数据。
- `POST /api/devices/bind`：绑定设备。
- `PUT /api/devices/{id}`：修改设备名称和安装位置，设备编号不可修改。
- `DELETE /api/devices/{id}`：软解绑设备，保留设备及历史监测数据。
- `GET /api/devices/{id}/current`：查询最新传感器数据。
- `GET /api/devices/{id}/history`：查询原始历史传感器数据。
- `GET /api/devices/{id}/trend`：按时间桶聚合历史浓度，返回平均值、最小值、最大值和样本数。
- `PUT /api/devices/{id}/threshold`：设置烟雾阈值。

设备列表参数均为可选：`keyword` 同时匹配设备编号、名称和位置；`status` 为 `0` 或 `1`；`page` 默认 `1`；`pageSize` 默认 `20`、最大 `200`。

绑定请求：

```json
{
  "deviceId": "SMOKE-001",
  "deviceName": "1号烟感",
  "location": "1栋101室"
}
```

历史查询参数均为可选，示例：

```text
GET /api/devices/1/history?start=2026-08-22T00:00:00&end=2026-08-22T23:59:59&limit=100
```

阈值请求：

```json
{"threshold": 2000}
```

修改设备资料：

```json
{"deviceName": "1号烟感", "location": "1栋101室"}
```

趋势查询默认统计最近 24 小时并按 60 分钟聚合，最多查询 31 天、返回 2000 个时间桶：

```text
GET /api/devices/1/trend?start=2026-08-22T00:00:00&end=2026-08-22T23:59:59&bucketMinutes=30
```

烟雾浓度、环境温湿度、电流、线缆温度、CO 值、趋势统计值和烟雾告警触发浓度均为 JSON 数字，可包含两位小数；蜂鸣器状态为字符串；阈值当前仍为正整数。旧历史数据的新增字段可能为 `null`。

## 数据接入

- `POST /api/telemetry`：上报烟雾浓度和可选扩展传感器数据，同时刷新设备在线状态并判断烟雾阈值。
- `POST /api/heartbeat`：上报心跳；设备恢复在线时自动处理已有离线告警。

浓度上报：

```json
{
  "deviceId": "SMOKE-001",
  "concentration": 20.37,
  "temperature": 27.43,
  "humidity": 59.42,
  "current": 2.01,
  "wireTemperature": 28.18,
  "coValue": 0.93,
  "beepStatus": "OFF",
  "messageId": "SMOKE-001-20260822-0001",
  "timestamp": "2026-08-22T10:00:00"
}
```

`concentration` 范围为 `0`–`1000000`。`temperature`、`humidity`、`current`、`wireTemperature`、`coValue` 和 `beepStatus` 可选；数值由服务端四舍五入保留两位小数，蜂鸣器状态会转为大写。`messageId` 和 `timestamp` 可选；设备重试时应复用同一 `messageId`，服务端会返回 `duplicate: true` 且不会重复入库。

心跳上报：

```json
{"deviceId": "SMOKE-001", "battery": 86}
```

`battery` 可选，取值范围为 `0` 到 `100`。

系统每 30 秒检查一次设备，超过 60 秒未上报心跳会将设备标记为离线并生成离线告警。两个时间参数可在 `application.yml` 中调整。

### MQTT 入站

设置 `MQTT_ENABLED=true` 后，后端会连接配置的 Broker 并订阅 `MQTT_TOPIC`。当前适配器用于接收华为云 IoTDA 规则转发消息，解析 `Smoke_Value`、`Temperature`、`Humidity`、`Current`、`WireTemperature`、`CO_Value` 和 `BeepStatus`，再复用同一遥测服务完成入库、在线状态和烟雾阈值判断。详细 payload、凭据和 Instance ID 说明见 [硬件接入文档](../hardware/README.md)。

`GET /api/system/capabilities` 中的 `mqtt=CONNECTED` 只说明后端订阅连接正常，不代表设备仍在上报。设备在线状态以最后心跳/遥测时间为准。

## 告警

- `GET /api/alerts`：分页查询告警，可使用 `deviceId`、`type`、`status`、`page`、`pageSize` 过滤。
- `POST /api/alerts/{id}/confirm`：确认告警。
- `POST /api/alerts/{id}/resolve`：完成并归档告警。

告警类型：`1` 为烟雾超标，`2` 为设备离线。告警状态：`0` 为未处理，`1` 为已确认，`2` 为已处理。

确认和处理接口不接收操作人字段，服务端使用 JWT 对应的当前登录用户名记录操作人。

## 广播

- `GET /api/broadcasts`：分页查询广播指令。
- `GET /api/broadcasts/{id}`：查询单条广播指令。
- `POST /api/broadcasts`：创建待下发广播指令。
- `PUT /api/broadcasts/{id}/status`：将指令标记为成功或失败。

创建请求：

```json
{
  "deviceId": "SMOKE-001",
  "content": "发现火情，请立即有序疏散",
  "triggerAlertId": 1
}
```

状态更新请求中，`1` 表示成功，`2` 表示失败：

```json
{"status": 1}
```

当前只完成广播指令的持久化和状态流转，真实 MQTT 发布将在接入 Broker 后由适配器消费待下发记录。
因此 `POST` 成功仅表示指令记录已创建，不表示目标设备已收到或播放。

## 告警复核与误报

- `POST /api/alerts/{id}/false-alarm`：将待处理或已确认告警标记为误报并归档。
- `POST /api/alerts/{id}/verify`：根据告警类型及记录中的浓度和阈值生成复核结论，并保存复核记录。已处置或误报告警会明确返回历史复核语义，不会描述为当前风险。

复核结论用于本地演示与人工处置辅助；尚未接入摄像头流或图像模型时，不会宣称其为真实视觉识别结果。

## 通知记录

- `GET /api/notifications?page=1&pageSize=50&alertId=&deviceId=&channel=&status=`：分页查询通知记录，支持按告警、设备、通道和投递状态筛选。
- `GET /api/notifications/{id}`：查询单条通知记录。
- `GET /api/notifications/summary`：查询 APP/SMS 与投递状态汇总。

通道仅支持 `APP`、`SMS`；投递状态为 `PENDING`、`SENT`、`FAILED`，筛选值忽略首尾空格和大小写。系统在创建烟雾或离线告警时会生成两条本地记录：通知中心可见的 APP 记录标记为 `SENT`；尚未接入供应商的 SMS 记录标记为 `PENDING`、`sentAt=null`，不会声称短信已发送。真实适配器后续可把 SMS 更新为 `SENT` 或 `FAILED`，前端字段不变。详见 [前端接口协作说明](FRONTEND_API.md)。

## 智能问答

- `POST /api/chat`：按本地安全知识库和当前告警上下文回答问题。RAG 服务通过 Ollama 调用 `gpt-oss:120b-cloud`；模型或服务不可用时分层回退到本地安全规则。

```json
{"question": "发生火情后怎么疏散？", "alertId": 1}
```

返回保留兼容字段 `answer`、`source`，并提供 `model`、`riskLevel`、`summary`、`immediateActions`、`verificationSteps`、`escalationConditions`、`safetyNotice` 和 `sources`。完整优化边界见 [智能问答优化记录](SMART_QA_OPTIMIZATION.md)。

## 设备接入凭据

- `POST /api/devices/bind`：管理员绑定新设备。响应中的 `deviceAccessToken` 仅返回这一次；服务端只保存其摘要。
- `POST /api/devices/{id}/credentials`：社区管理员或系统管理员轮换设备令牌。旧令牌立即失效。

生产环境的 `POST /api/telemetry` 与 `POST /api/heartbeat` 必须包含请求头：

```text
X-Device-Token: <deviceAccessToken>
```

开发配置默认关闭该校验，便于本地模拟；`prod` 配置中无法关闭。

## 生产接口限制

- Swagger/OpenAPI 仅在开发配置提供，生产配置默认关闭。
- 登录接口按客户端地址进行单实例失败限流：默认 15 分钟内连续失败 5 次后锁定 15 分钟。
- `/api/**` 的审计日志只记录方法、路径、状态、耗时、用户和来源地址，不记录请求体、密码、JWT 或设备令牌。
