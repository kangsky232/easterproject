# 后端接口

更新日期：2026-09-01。开发环境可通过 `/swagger-ui.html` 查看由代码生成的 OpenAPI 文档；本文件记录业务语义和协作约定。

统一响应格式：

```json
{"code": 0, "message": "success", "data": {}}
```

业务错误通过对应的 HTTP `4xx/5xx` 状态返回，响应体同时包含非零 `code` 和 `message`。

常见状态：`400` 参数错误、`401` 未登录或会话失效、`403` 权限不足、`404` 资源不存在、`409` 状态冲突、`503` 依赖服务不可用。前端必须同时判断 HTTP 状态和响应体 `code`。仅当部署方主动启用登录限流时，登录接口才可能返回 `429`。

除健康检查、系统能力、登录、设备遥测和心跳外，其余接口需要请求头：

```text
Authorization: Bearer <token>
```

全新开发数据库会按 `application-dev.yml` 的引导配置创建管理员；已有数据库不会自动覆盖现有账号密码。团队联调应使用负责人分发的开发账号，生产部署必须通过环境变量设置独立管理员和强 JWT 密钥。

本地开发如遗失引导管理员密码，可在**单次启动**时同时设置 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 和 `BOOTSTRAP_ADMIN_RESET_PASSWORD=true`。该开关默认关闭，生产环境会拒绝启动；重置后必须移除该环境变量。

## 系统

- `GET /api/health`：健康检查，会执行 `SELECT 1` 验证 MySQL；数据库不可用时返回 HTTP `503`。
- `GET /api/system/capabilities`：查询存储、设备接入、MQTT、视觉 AI、知识服务和广播模块的当前接入状态；`visualAi` 为 `DISABLED`、`SIMULATION_FALLBACK` 或 `DEEPSEEK_VISION`。`knowledgeBase=CONNECTED` 只表示 RAG Flask 健康接口可达，返回的提供方/模型名也是服务配置标签，并不证明 Ollama 已完成一次推理。运行模式来自 Spring Profile。
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

- `GET /api/auth/workspace`：返回当前数据库用户所属角色的专属工作台，包括 `roleCode`、`roleLabel`、`homeTitle`、`description`、可见 `modules` 和 `permissions`。前端据此生成差异化导航，但后端接口鉴权仍是最终权限边界。

## 用户管理

主前端已为 `SYSTEM_ADMIN` 集成用户管理模块，并复用当前 JWT 调用以下接口，不需要跨域二次登录。后端仍提供独立备用管理页面：`http://127.0.0.1:8080/admin/index.html`；`/admin` 和 `/admin/` 会在服务端内部转发到该文件，不生成可能造成 HTTPS 降级的外部重定向。

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
- `PUT /api/devices/{id}/threshold`：兼容烟雾阈值接口；当前安全规则固定只接受 `100` ppm。

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
{"threshold": 100}
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

## 模拟 3D 地图

- `GET /api/map/scene`：所有已登录角色均可读取模拟社区场景，返回楼栋尺寸/坐标、楼层数，以及设备楼栋、楼层、房间、局部坐标、在线/告警状态和最新遥测。
- `PUT /api/map/devices/{id}/position`：仅小区管理员和系统管理员可修改设备地图位置。

位置更新请求：

```json
{
  "buildingCode": "A1",
  "floorNo": 3,
  "roomLabel": "301",
  "positionX": 5.5,
  "positionZ": 6.0
}
```

`floorNo` 不得超过目标楼栋层数，`positionX`/`positionZ` 必须处于楼栋宽度和深度范围内。场景数据存储在 `map_building` 与 `device_map_position`；未配置位置的已绑定设备在首次读取场景时会自动获得模拟位置。

## AI 视觉实时巡检

- `GET /api/vision/status`：返回巡检开关、当前是否分析、DeepSeek 是否配置、运行模式、模型、轮换周期、阈值、当前模拟帧、最近分析和最近事件。
- `GET /api/vision/events?status=&page=1&pageSize=50`：分页查询视觉事件；`status` 可选 `PENDING_REVIEW`、`CONFIRMED_FIRE`、`FALSE_ALARM`。
- `GET /api/vision/summary`：返回待人工判断、已确认为火情、已排除误报和总事件数。
- `POST /api/vision/simulation/next`：立即分析下一张模拟帧；仅消防员、小区管理员和系统管理员可用。
- `POST /api/vision/events/{id}/review`：人工提交一次性结论；仅消防员、小区管理员和系统管理员可用。

复核请求：

```json
{
  "verdict": "CONFIRMED_FIRE",
  "remark": "已电话通知值班人员，现场确认配电箱冒烟"
}
```

`verdict` 仅支持 `CONFIRMED_FIRE` 与 `FALSE_ALARM`，`remark` 必填且最多 500 字。后端从 JWT 写入复核账号与服务器时间；已复核事件再次提交返回 `409`，不能覆盖原结论。

后端默认每 15 秒从 15 张静态模拟图片中洗牌式随机取一张：每轮全部出现一次，跨轮相邻画面也不会重复；素材包含 10 张正常和 5 张疑似烟火。配置 `DEEPSEEK_API_KEY` 后，请求以外部 HTTPS 图片 URL 发送到 DeepSeek `deepseek-v4-flash-vision-exp`；未配置时分析结果的 `mode` 为 `SIMULATION_FALLBACK`，配置后调用失败则为 `DEEPSEEK_ERROR`，失败帧不会创建疑似事件。模型或模拟规则只有在 `suspectedFire=true` 且 `confidence` 达到阈值时才建档；同一机位已有待复核事件时不会重复建档或重复推送。

事件的 `dingtalkStatus` 为 `PENDING`、`SENT`、`FAILED` 或 `SKIPPED`，并保存成功接收人数或失败原因。钉钉消息会明确注明画面来自模拟轮播、不是现场摄像头；人工复核后会再发送结果通知。AI 结果只用于提示和分流，不能自动替代现场核验、119 报警或法定消防设施。

## 隐患闭环

- `GET /api/hazards?status=&priority=&page=1&pageSize=100`：分页查询隐患；居民只返回本人上报记录，消防员、小区管理员和系统管理员返回全部记录。
- `GET /api/hazards/summary`：按同样可见范围返回 `reported`、`processing`、`pendingReview`、`closed` 和 `openTotal`。
- `GET /api/hazards/{id}`：返回工单与按时间排序的 `actions` 流转记录；居民不能读取他人记录。
- `POST /api/hazards`：所有已登录角色均可上报隐患。
- `POST /api/hazards/{id}/claim`：消防员、小区管理员或系统管理员接单，状态从 `REPORTED` 进入 `PROCESSING`。
- `POST /api/hazards/{id}/submit`：当前接单人或管理员提交整改结果，状态进入 `PENDING_REVIEW`。
- `POST /api/hazards/{id}/review`：仅小区管理员或系统管理员复核；通过进入 `CLOSED`，驳回回到 `PROCESSING`。

上报请求：

```json
{
  "title": "消防通道堆放杂物",
  "description": "2号楼5层西侧楼道堆放纸箱，影响疏散。",
  "location": "2号楼5层西侧楼道",
  "priority": "HIGH"
}
```

`priority` 只能是 `LOW`、`MEDIUM`、`HIGH`、`URGENT`；`status` 只能是 `REPORTED`、`PROCESSING`、`PENDING_REVIEW`、`CLOSED`。提交整改使用 `{"resolution":"已清理纸箱并完成现场检查"}`；复核使用 `{"approved":true,"remark":"现场复核通过"}`。驳回时 `approved=false` 且 `remark` 必填。上报人、接单人、复核人与时间均由后端根据当前 JWT 和服务器时间写入，前端不能伪造。

## 数据接入

- `POST /api/telemetry`：上报烟雾浓度和可选扩展传感器数据，同时刷新设备在线状态并执行多指标告警判断。
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

系统每 3 秒检查一次设备，超过 60 秒未上报心跳会将设备标记为离线并生成离线告警。两个时间参数可在 `application.yml` 中调整。

### MQTT 入站

设置 `MQTT_ENABLED=true` 后，后端会连接配置的 Broker 并订阅 `MQTT_TOPIC`。当前适配器用于接收华为云 IoTDA 规则转发消息，解析 `Smoke_Value`、`Temperature`、`Humidity`、`Current`、`WireTemperature`、`CO_Value` 和 `BeepStatus`，再复用同一遥测服务完成入库、在线状态和多指标告警判断。详细 payload、凭据和 Instance ID 说明见 [硬件接入文档](../hardware/README.md)。

HTTP 的幂等依赖调用方重试时复用 `messageId`。MQTT 订阅器目前为每次接收生成 `设备号:当前时间戳`，QoS 1 消息被 Broker 重投时可能重复入库，不能把 HTTP 的幂等承诺扩展到 MQTT。

`GET /api/system/capabilities` 中的 `mqtt=CONNECTED` 只说明后端订阅连接正常，不代表设备仍在上报。设备在线状态以最后心跳/遥测时间为准。

## 告警

- `GET /api/alerts`：分页查询告警，可使用 `deviceId`、`type`、`status`、`page`、`pageSize` 过滤。
- `POST /api/alerts/{id}/confirm`：确认告警。
- `POST /api/alerts/{id}/resolve`：完成并归档告警。

告警类型：`1` 烟雾、`2` 设备离线、`3` 环境温度、`4` 环境湿度、`5` 电气电流、`6` 线缆温度、`7` 一氧化碳。`severity` 为 `WARNING`（预警）或 `DANGER`（危险），`ruleDescription` 记录实际触发规则。告警状态：`0` 为未处理，`1` 为已确认，`2` 为已处理。

传感器告警规则：

| 指标 | 预警 | 危险 |
| --- | --- | --- |
| 烟雾浓度 | `>= 100 ppm` | `> 300 ppm` |
| 环境温度 | `> 45℃`，或 5 分钟内升温 `>= 10℃` | `> 60℃` |
| 环境湿度 | 5 分钟内下降 `>= 20` 个百分点 | `< 20%` |
| 电气电流 | `> 10A`，或 5 分钟内波动 `>= 5A` | `> 15A` |
| 线缆温度 | `> 70℃` | `> 90℃` |
| 一氧化碳 | `50–100 ppm` | `> 100 ppm` |

危险规则优先于预警规则；表格未覆盖的中间区间只用于趋势观察，不会单独建警。MQ-2 的 ppm 是近似估算值，接口和规则结果不能替代经过认证的消防探测结论。完整业务口径见 [业务场景与迭代路线](BUSINESS_SCENARIO_AND_ROADMAP.md)。

同一设备、同一指标的活动预警只通知一次；从预警升级为危险时再次通知。告警被处置或标记误报后，如果后续数据再次进入告警范围，会创建新告警。

确认和处理接口不接收操作人字段，服务端使用 JWT 对应的当前登录用户名记录操作人。

## 广播

消防员、小区管理员和系统管理员可读取、创建和再次下发广播；删除记录和手工更新状态仅小区管理员、系统管理员可用。

- `GET /api/broadcasts`：消防员、小区管理员、系统管理员分页查询广播指令。
- `GET /api/broadcasts/{id}`：消防员、小区管理员、系统管理员查询单条广播指令。
- `POST /api/broadcasts`：创建待下发广播指令。
- `POST /api/broadcasts/{id}/deliver`：将已有广播再次下发到钉钉，并更新状态和执行时间。
- `DELETE /api/broadcasts/{id}`：删除广播记录；不会撤回已经送达钉钉的消息。
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

创建广播时会先保存 `PENDING` 记录；如果钉钉已配置，后端会立即尝试向全部启用且已绑定的员工单聊投递，并把记录更新为成功或失败；如果钉钉未配置，记录保持待下发。`/deliver` 用于再次执行同一钉钉投递流程。

MQTT 设备下行仍未实现。因此广播状态成功只表示钉钉单聊投递调用成功，不表示目标烟感设备已收到或播放；删除数据库记录也不会撤回已送达的钉钉消息。

## 告警复核与误报

- `POST /api/alerts/{id}/false-alarm`：将待处理或已确认告警标记为误报并归档。
- `POST /api/alerts/{id}/verify`：根据告警类型及记录中的浓度和阈值生成复核结论，并保存复核记录。已处置或误报告警会明确返回历史复核语义，不会描述为当前风险。

复核结论用于本地演示与人工处置辅助；尚未接入摄像头流或图像模型时，不会宣称其为真实视觉识别结果。

## 通知审计闭环

以下读取接口仅消防员、小区管理员和系统管理员可用，居民工作台不展示通知审计数据：

- `GET /api/notifications?page=1&pageSize=50&alertId=&deviceId=&channel=&status=&auditStatus=`：分页查询通知记录，支持按告警、设备、通道、投递状态和核查状态筛选。
- `GET /api/notifications/{id}`：查询单条通知记录。
- `GET /api/notifications/summary`：查询 APP/SMS/钉钉、投递状态、待核查、已核查和异常待办汇总。
- `POST /api/notifications/{id}/audit`：消防员、小区管理员或系统管理员填写核查结果与结论，记录当前账号和完成时间；已核查记录不可重复覆盖。

通道支持 `APP`、`SMS`、`DINGTALK`；投递状态为 `PENDING`、`SENT`、`FAILED`，核查状态为 `PENDING`、`COMPLETED`，筛选值忽略首尾空格和大小写。核查请求示例为 `{"result":"FOLLOWED_UP","remark":"已核对钉钉接收人绑定并通知值班人员"}`，其中 `result` 支持 `NORMAL`、`FOLLOWED_UP`，`remark` 必填且最多 500 字。系统创建告警时，APP 记录标记为 `SENT`；未接入供应商的 SMS 记录标记为 `PENDING`、`sentAt=null`；配置钉钉后会真实投递并按接口结果把 DINGTALK 记录标记为 `SENT` 或 `FAILED`。审计核查只表示工作人员检查了投递日志，不代表外部接收人已读。详见 [前端接口协作说明](FRONTEND_API.md)。

## 智能问答

- `POST /api/chat`：按本地安全知识库和当前告警上下文回答问题。RAG 服务可通过 Ollama 调用 `gpt-oss:120b-cloud`；模型或服务不可用时分层回退到本地安全规则。`knowledgeBase` 只能判断 RAG 服务是否可达，单次回答是否实际经过 Ollama 应查看响应 `source=OLLAMA`。

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
- 登录失败锁定默认关闭，连续输错不会再禁用 15 分钟。如生产环境需要，可设置 `LOGIN_RATE_LIMIT_ENABLED=true` 主动启用，或优先在网关/Cloudflare 层配置限流。
- `/api/**` 的审计日志只记录方法、路径、状态、耗时、用户和来源地址，不记录请求体、密码、JWT 或设备令牌。
