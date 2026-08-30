# 前端接口协作说明

契约版本：2026-08-26。接口字段以本文件和开发环境 OpenAPI 为准；功能是否为真实外部集成，请同时查看 [功能状态](PROJECT_STATUS.md)。

本文面向 Web、移动端和后续管理端开发。开发环境默认后端地址为 `http://127.0.0.1:8080`；Vite 开发时可直接使用 `/api` 代理。

生产环境必须使用实际的 HTTPS 域名，不要在客户端写死 `localhost` 或 IP。Cloudflare Pages 构建通过 `VITE_API_BASE` 注入后端地址；这是构建时变量，修改后必须重新部署。

## 统一约定

除文件下载外，所有接口响应均为：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code = 0`：成功。
- 非零 `code` 与 HTTP `4xx/5xx`：业务或请求失败，直接显示 `message`。
- 分页数据统一为：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

日期时间均为 ISO-8601 本地时间，例如 `2026-08-24T14:30:00`。

## 登录与会话

### 登录

`POST /api/auth/login`，无需令牌。

```json
{"username":"用户名","password":"密码"}
```

成功的 `data`：

```json
{
  "token": "JWT",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "username": "admin",
    "displayName": "系统管理员",
    "role": "SYSTEM_ADMIN"
  }
}
```

后续受保护接口统一携带：

```text
Authorization: Bearer <token>
```

### 客户端行为

1. 登录成功后保存 `token` 和 `user`。
2. 刷新页面时调用 `GET /api/auth/me` 恢复会话。
3. 任意接口返回 HTTP `401` 时，立即清除本地令牌并跳转登录页。
4. 本人改密、管理员重置密码、账号被禁用后，旧 JWT 会立即失效；改密成功后前端应主动退出并要求用新密码登录。

### 当前用户与改密

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/auth/me` | 无 | 获取当前用户 |
| GET | `/api/auth/workspace` | 无 | 获取当前角色可见模块、权限和工作台文案 |
| POST | `/api/auth/password` | `{"currentPassword":"旧密码","newPassword":"新密码"}` | 本人改密，密码至少 8 位 |

## 角色权限

| 角色 | 说明 | 前端可提供的主要操作 |
| --- | --- | --- |
| `RESIDENT` | 居民 | “我的安全”只读监控、社区 3D 地图和安全问答 |
| `FIREFIGHTER` | 消防人员 | 应急总览、3D 态势、告警处置、通知追踪和应急广播 |
| `COMMUNITY_ADMIN` | 社区管理员 | 小区监控、设备管理、3D 位置管理、告警处置、通知和广播管理 |
| `SYSTEM_ADMIN` | 系统管理员 | 全部社区管理员能力 + 用户管理入口与通知审计视图 |

登录后前端调用 `/api/auth/workspace` 生成角色专属导航，并在状态层再次拦截无权限操作；后端仍会强制校验，收到 `403` 时应显示“没有操作权限”。居民不能读取通知与广播记录；开发态“模拟告警”仅向社区管理员和系统管理员显示，生产模式隐藏。

## 系统与大屏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 公开健康检查；MySQL 不可用时返回 503 |
| GET | `/api/system/capabilities` | 公开能力状态，前端可据此展示 MQTT、视觉 AI、知识库、广播的接入状态 |
| GET | `/api/dashboard/overview` | 设备总数、在线数、离线数、活动告警数 |

`/api/dashboard/overview` 的 `data`：

```json
{"totalDevices":12,"onlineDevices":10,"offlineDevices":2,"activeAlerts":1}
```

## 设备

| 方法 | 路径 | 角色 | 请求/说明 |
| --- | --- | --- | --- |
| GET | `/api/devices?keyword=&status=&page=1&pageSize=20` | 已登录 | `status` 为 `0` 离线或 `1` 在线；关键词匹配设备编号、名称、位置 |
| GET | `/api/devices/{id}` | 已登录 | 单设备详情 |
| POST | `/api/devices/bind` | 社区管理员、系统管理员 | 绑定设备 |
| PUT | `/api/devices/{id}` | 社区管理员、系统管理员 | 更新名称和位置 |
| PUT | `/api/devices/{id}/threshold` | 社区管理员、系统管理员 | 更新阈值 |
| DELETE | `/api/devices/{id}` | 社区管理员、系统管理员 | 软解绑定，保留历史数据 |
| POST | `/api/devices/{id}/credentials` | 社区管理员、系统管理员 | 轮换设备接入令牌，明文仅返回一次 |
| GET | `/api/devices/{id}/current` | 已登录 | 最新传感器数据 |
| GET | `/api/devices/{id}/history?start=&end=&limit=100` | 已登录 | 原始历史传感器数据，`limit` 为 1–1000 |
| GET | `/api/devices/{id}/trend?start=&end=&bucketMinutes=60` | 已登录 | 聚合趋势点 |

绑定请求：

```json
{"deviceId":"SMOKE-001","deviceName":"1号楼烟感","location":"1号楼101室"}
```

更新设备：

```json
{"deviceName":"1号楼烟感","location":"1号楼101室"}
```

更新阈值：

```json
{"threshold":100}
```

列表项的关键字段：

```json
{
  "id": 1,
  "deviceId": "SMOKE-001",
  "deviceName": "1号楼烟感",
  "location": "1号楼101室",
  "threshold": 100,
  "battery": 86,
  "latestConcentration": 20.37,
  "latestTemperature": 27.43,
  "latestHumidity": 59.42,
  "latestCurrent": 2.01,
  "latestWireTemperature": 28.18,
  "latestCoValue": 0.93,
  "latestBeepStatus": "OFF",
  "latestTimestamp": "2026-08-24T14:30:00",
  "online": true
}
```

所有数值遥测字段使用 JSON `number`，后端最多保留两位小数；前端统一显示两位小数。扩展数据在旧历史记录中可能为 `null`，此时前端显示 `--`。`latestBeepStatus` 通常为 `ON` / `OFF`；烟雾预警阈值按当前规则固定为 `100` ppm。

## 模拟 3D 地图

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/map/scene` | 已登录 | 返回模拟社区、楼栋和带实时状态的设备空间位置 |
| PUT | `/api/map/devices/{id}/position` | 社区管理员、系统管理员 | 保存楼栋、楼层、房间和楼内 X/Z 坐标 |

地图场景的 `buildings` 用于绘制 3D 楼栋，`devices` 包含 `status`（`ONLINE` / `OFFLINE` / `ALARM`）、`alertSeverity`、楼层/房间、坐标和最新六类遥测。居民与消防员看到只读地图，小区管理员和系统管理员显示位置编辑表单。前端修改位置后必须重新拉取场景，不在本地伪造保存成功。

## 告警

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/alerts?deviceId=&type=&status=&page=1&pageSize=20` | 已登录 | `type`：`1` 烟雾、`2` 离线、`3` 温度、`4` 湿度、`5` 电流、`6` 线温、`7` CO；`status`：`0` 待处理、`1` 已确认、`2` 已处理 |
| POST | `/api/alerts/{id}/confirm` | 消防人员及以上 | 确认告警 |
| POST | `/api/alerts/{id}/resolve` | 消防人员及以上 | 处理并归档 |
| POST | `/api/alerts/{id}/false-alarm` | 消防人员及以上 | 标记误报并归档 |
| POST | `/api/alerts/{id}/verify` | 消防人员及以上 | 获得辅助复核结论 |

前端操作成功后应刷新告警列表、仪表盘摘要和通知列表。告警操作人由后端 JWT 自动写入，前端不要传操作人字段。

传感器告警额外返回 `severity`（`WARNING` / `DANGER`）和 `ruleDescription`。兼容字段 `concentration` 对非烟雾告警表示该指标的触发值，单位由 `alertType` 决定。

## 通知中心

通知记录接口面向前端列表、筛选、详情和统计卡片。APP 表示通知中心本地记录，状态为 `SENT`；SMS 尚未接入供应商，只生成 `PENDING` 占位记录；DINGTALK 表示钉钉真实投递结果。

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/notifications?page=1&pageSize=50&alertId=&deviceId=&channel=&status=` | 消防人员及以上 | 分页筛选通知 |
| GET | `/api/notifications/{id}` | 消防人员及以上 | 通知详情 |
| GET | `/api/notifications/summary` | 消防人员及以上 | 所有通知的通道与状态汇总 |

筛选值：

- `channel`：`APP`、`SMS`、`DINGTALK`
- `status`：`PENDING`、`SENT`、`FAILED`

筛选值忽略首尾空格和大小写。前端展示状态时：`PENDING` 使用“待发送”，`SENT` 使用“已送达”，`FAILED` 使用“失败”；SMS 为 `PENDING` 时不得提示用户“短信已发送”。

单条通知 `data`：

```json
{
  "id": 18,
  "alertId": 9,
  "deviceId": "SMOKE-001",
  "channel": "SMS",
  "receiver": "未配置",
  "content": "设备 SMOKE-001 触发烟雾超阈值告警，请及时处理。",
  "status": "PENDING",
  "sentAt": null,
  "createdAt": "2026-08-24T14:30:00"
}
```

摘要 `data`：

```json
{"total":18,"appCount":9,"smsCount":9,"pendingCount":9,"sentCount":9,"failedCount":0}
```

## 广播

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/broadcasts?page=1&pageSize=20` | 消防人员及以上 | 广播记录 |
| GET | `/api/broadcasts/{id}` | 消防人员及以上 | 单条广播 |
| POST | `/api/broadcasts` | 消防人员及以上 | 创建待下发广播 |
| POST | `/api/broadcasts/{id}/deliver` | 消防人员及以上 | 再次下发已有广播并更新结果 |
| DELETE | `/api/broadcasts/{id}` | 社区管理员、系统管理员 | 删除记录（不撤回钉钉消息） |
| PUT | `/api/broadcasts/{id}/status` | 社区管理员、系统管理员 | 更新下发状态 |

创建请求：

```json
{"deviceId":"SMOKE-001","content":"发现火情，请立即有序疏散。","triggerAlertId":9}
```

状态请求：`{"status":1}`，其中 `0` 待下发、`1` 成功、`2` 失败。当前广播仅完成持久化与状态流转，尚未通过 MQTT 发至设备。

主前端提供“广播记录”页。能力值为 `broadcast=PERSISTENCE_ONLY` 时，创建弹窗和记录页必须提示“仅保存记录”，不得将创建成功表述为已送达。

## 智能问答

`POST /api/chat`，已登录：

```json
{"question":"发现火情后如何疏散？","alertId":9}
```

返回：

```json
{
  "answer": "兼容的纯文本回答",
  "source": "OLLAMA",
  "model": "gpt-oss:120b-cloud",
  "riskLevel": "HIGH",
  "summary": "一句话安全结论",
  "immediateActions": ["立即措施"],
  "verificationSteps": ["核验步骤"],
  "escalationConditions": ["升级条件"],
  "safetyNotice": "安全提示",
  "sources": [{"id": "evacuation", "title": "火情疏散指引"}]
}
```

结构化字段存在时，前端按安全处置卡片展示；字段缺失时仍可直接展示 `answer`。问答使用本地知识检索并通过 Ollama 调用 `gpt-oss:120b-cloud`，不可用时返回内置安全规则答案。不得将回答作为真实视觉识别结论。详见 [智能问答优化记录](SMART_QA_OPTIMIZATION.md)。

## 用户管理 API

以下接口只对 `SYSTEM_ADMIN` 开放。主监控前端的“用户管理”模块复用当前 JWT，支持列表筛选、创建、编辑、启停、重置密码和删除；独立后端页面 `/admin/index.html` 仅作为备用入口。

| 方法 | 路径 | 请求体 |
| --- | --- | --- |
| GET | `/api/users?page=1&pageSize=20&keyword=&role=&enabled=` | 无 |
| GET | `/api/users/{id}` | 无 |
| POST | `/api/users` | `{"username":"u","password":"至少8位","displayName":"名称","role":"RESIDENT","phone":""}` |
| PUT | `/api/users/{id}` | `{"displayName":"名称","role":"RESIDENT","phone":""}` |
| PUT | `/api/users/{id}/status` | `{"enabled":0}` |
| PUT | `/api/users/{id}/password` | `{"password":"至少8位"}` |
| DELETE | `/api/users/{id}` | 无 |

保护规则：不能删除或禁用自己；不能修改自己的角色；至少保留一个启用的系统管理员；重置密码不能用于当前账号（使用本人改密接口）。

## 设备上报接口

该部分由硬件/网关调用，普通前端无需调用：

| 方法 | 路径 | 请求体 |
| --- | --- | --- |
| POST | `/api/telemetry` | `{"deviceId":"SMOKE-001","concentration":20.37,"temperature":27.43,"humidity":59.42,"current":2.01,"wireTemperature":28.18,"coValue":0.93,"beepStatus":"OFF","messageId":"唯一消息ID","timestamp":"2026-08-24T14:30:00"}` |
| POST | `/api/heartbeat` | `{"deviceId":"SMOKE-001","battery":86}` |

生产环境必须携带 `X-Device-Token`。`messageId` 应由设备复用以支持去重重试。

## 开发联调清单

1. 登录后在 API 客户端自动注入 `Authorization: Bearer <token>`。
2. 调用 `/api/auth/workspace`，只渲染返回的模块；不能仅依赖本地保存的角色字符串。
3. 当前主前端每 3 秒刷新后端数据与 3D 地图状态，与设备上报周期保持一致；主面板展示最新扩展传感器字段，实时烟雾趋势使用最近 120 条原始点，24 小时/7 天/30 天视图调用聚合趋势接口。
4. 统一处理 `401`（重新登录）与 `403`（无权限）。
5. 列表页保留筛选参数，所有分页接口最大 `pageSize` 为 200。
6. 不向前端返回或记录密码、JWT、设备明文令牌；设备轮换令牌只显示一次。

开发环境还可打开 `http://127.0.0.1:8080/swagger-ui.html` 查看自动生成的 OpenAPI 文档。

Cloudflare Pages 与本机后端联调、CORS 和断连排查见 [CLOUDFLARE_PAGES.md](CLOUDFLARE_PAGES.md)。
