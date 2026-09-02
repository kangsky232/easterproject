# 前端接口协作说明

契约版本：2026-09-02。接口字段以本文件和开发环境 OpenAPI 为准；功能是否为真实外部集成，请同时查看 [功能状态](PROJECT_STATUS.md)。

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

当前 Vue 客户端把会话保存在同源 `localStorage` 的 `smart-smoke.token` 和 `smart-smoke.user` 中，API 封装会在每次请求时读取当前令牌。同一浏览器普通窗口的多个页签因此共享账号，第二次登录会覆盖第一个账号。并行验收不同角色时使用普通窗口与无痕窗口、不同浏览器或不同浏览器配置文件。

### 当前用户与改密

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/auth/me` | 无 | 获取当前用户 |
| GET | `/api/auth/workspace` | 无 | 获取当前角色可见模块、权限和工作台文案 |
| POST | `/api/auth/password` | `{"currentPassword":"旧密码","newPassword":"新密码"}` | 本人改密，密码至少 8 位 |

## 角色权限

| 角色 | 说明 | 前端可提供的主要操作 |
| --- | --- | --- |
| `RESIDENT` | 居民 | 欢迎首页、实时监控、社区三维态势、隐患管理；右下角智能问答，可上报并跟踪本人隐患，其余业务只读 |
| `FIREFIGHTER` | 消防人员 | 欢迎首页、实时监控、社区三维态势、隐患管理、通知审计、广播管理；右下角智能问答，可处置告警、接单整改、核查通知和下发广播 |
| `COMMUNITY_ADMIN` | 社区管理员 | 欢迎首页、实时监控、社区三维态势、设备管理、隐患管理、通知审计、广播管理；右下角智能问答，可管理设备/地图、处理和复核隐患及核查通知 |
| `SYSTEM_ADMIN` | 系统管理员 | 社区管理员全部页面 + 用户管理；右下角智能问答，拥有全部业务管理操作 |

登录后前端先显示欢迎首页，调用 `/api/auth/me` 同步数据库中的最新角色，再使用 `/api/auth/workspace` 返回的可见模块过滤本地定义顺序中的平铺导航和功能页签。智能问答不占用导航板块，而是以右下角全局挂件呈现，拥有 `chat` 模块权限的账号点击后打开非模态白色小窗口。无权模块不提供导航入口，相关操作按钮按权限隐藏；功能区中的部分 Vue 组件使用 `v-show`，可能已经挂载，因此不能把前端隐藏当作权限边界。工作区加载失败时仅保留实时监控、社区三维态势和智能问答挂件三个安全只读入口，不继承上一账号的管理权限。后端仍会强制校验，收到 `403` 时应显示“没有操作权限”。居民不能读取通知与广播记录；开发态“模拟告警”仅向社区管理员和系统管理员显示，生产模式隐藏。

## 系统与大屏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 公开健康检查；MySQL 不可用时返回 503 |
| GET | `/api/system/capabilities` | 公开能力状态，前端可据此展示 MQTT、视觉 AI、知识库、广播的接入状态 |
| GET | `/api/dashboard/overview` | 设备总数、在线数、离线数、活动告警数 |

能力值属于运行时状态而不是版本承诺。`visualAi` 当前支持 `DISABLED`、`SIMULATION_FALLBACK`、`DEEPSEEK_VISION`；2026-09-02 本机已配置 DeepSeek Key，检查结果为 `DEEPSEEK_VISION`。每次验收都应重新请求接口，且配置已加载不等于模型一定完成了一次成功推理。

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

## 社区三维态势

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/map/scene` | 已登录 | 返回模拟社区、楼栋和带实时状态的设备空间位置 |
| PUT | `/api/map/devices/{id}/position` | 社区管理员、系统管理员 | 保存楼栋、楼层、房间和楼内 X/Z 坐标 |

地图场景的 `buildings` 用于绘制 3D 楼栋，`devices` 包含 `status`（`ONLINE` / `OFFLINE` / `ALARM`）、`alertSeverity`、楼层/房间、坐标和最新六类遥测。后端只有在设备已绑定、持久化状态为在线且最后心跳不早于 `smoke.offline-timeout-seconds`（默认 60 秒）时才返回 `ONLINE`；传感器预警/危险仍优先返回 `ALARM`。前端把可见立面按楼层切分为点击区域，并把设备点投影到最近的可见楼体立面，避免楼体深度与透视造成跨层错位；当前数据库中的 501 设备应显示在 5 层。楼栋、楼层按钮、设备列表和右侧详情联动；地图每 3 秒刷新时保留用户当前楼层和管理员尚未提交的位置表单。若地图请求失败，旧快照中的在线设备会降级显示为离线，并显示状态过期提示，避免继续冒充实时在线。居民与消防员看到只读地图，小区管理员和系统管理员显示位置编辑表单。前端修改位置后必须重新拉取场景，不在本地伪造保存成功。

当前过道主视角、公共区域和 AI 预警复核共使用 21 张静态演示图片。前端按楼栋编号排序并累计楼层索引，为当前 19 个楼层逐一分配 19 张独立正常图；第二常规机位使用错位索引，保证同层不重复，另有 2 张烟雾预警图。界面明确标注“模拟画面 / 尚未接入真实摄像头”。这些图片不属于 `/api/map/scene` 返回值，也不能作为真实视觉复核结果；后续接入摄像头时应补充摄像头目录、流地址/回放地址、访问鉴权和模型结果接口。

## AI 视觉实时巡检

社区三维态势页内嵌视觉巡检面板，并每 3 秒轮询状态、事件与汇总：

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/api/vision/status` | 已登录 | 自动巡检运行状态、当前模拟帧、最近分析、模型/降级状态 |
| GET | `/api/vision/events?status=&page=1&pageSize=50` | 已登录 | 视觉事件分页 |
| GET | `/api/vision/summary` | 已登录 | 待判断、已确认、已排除统计 |
| POST | `/api/vision/simulation/next` | 消防员、小区管理员、系统管理员 | 调试用单帧分析，主前端无此按钮 |
| POST | `/api/vision/patrol/start` | 消防员、小区管理员、系统管理员 | 立即识别第一帧并持续自动巡检 |
| POST | `/api/vision/patrol/pause` | 消防员、小区管理员、系统管理员 | 暂停自动巡检和后续自动告警 |
| POST | `/api/vision/events/{id}/review` | 消防员、小区管理员、系统管理员 | 提交人工结论 |

`VisionStatus.running` 决定前端展示“巡检运行中”还是“巡检已暂停”；后端每次启动时该值默认 `false`。`deepSeekConfigured` 决定前端展示“DeepSeek”还是“模拟规则降级”。单帧 `latestAnalysis.mode` 还可能为 `DEEPSEEK_ERROR`；此时必须显示错误，不能展示为安全结论。`currentFrame.frameKey` 对应模拟图片键，`imageUrl` 是 DeepSeek 可访问的 Pages 图片地址。

事件状态为 `PENDING_REVIEW`、`CONFIRMED_FIRE`、`FALSE_ALARM`；复核请求为 `{"verdict":"CONFIRMED_FIRE","remark":"现场核验依据"}`。拥有 `VISION_REVIEW` 权限的账号显示“立即分析下一帧”和复核按钮，居民只能查看。提交成功后刷新事件与统计；后端返回 `409` 时说明其他工作人员已先完成判断，不能覆盖原结论。

`dingtalkStatus` 为 `PENDING`、`SENT`、`FAILED`、`SKIPPED`。界面必须始终提示当前是模拟图片轮播；即使分析模式为 `DEEPSEEK_VISION`，也不能描述为真实摄像头实时画面。人工确认只是系统处置留痕，发生真实火情仍按现场流程报警。

## 隐患管理

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/hazards?status=&priority=&page=1&pageSize=200` | 已登录 | 居民读取本人记录，其余角色读取全部；支持状态和优先级筛选 |
| GET | `/api/hazards/summary` | 已登录 | 返回各状态数量与 `openTotal`，用于隐患页统计和监控首页 KPI |
| GET | `/api/hazards/{id}` | 已登录且有权查看 | 返回 `ticket` 和完整 `actions` 时间线 |
| POST | `/api/hazards` | 已登录 | 上报标题、说明、位置和优先级 |
| POST | `/api/hazards/{id}/claim` | 消防人员及以上 | 接单并进入整改中 |
| POST | `/api/hazards/{id}/submit` | 当前接单人或管理员 | 提交整改结果并进入待复核 |
| POST | `/api/hazards/{id}/review` | 社区管理员、系统管理员 | 通过后关闭；驳回后返回整改中 |

前端状态机固定为 `REPORTED → PROCESSING → PENDING_REVIEW → CLOSED`；复核驳回是 `PENDING_REVIEW → PROCESSING`。页面不得直接跳过接单或复核，也不允许用本地状态冒充保存成功。每次成功操作后重新读取列表、详情、时间线和摘要。优先级为 `LOW`、`MEDIUM`、`HIGH`、`URGENT`，复核驳回必须填写原因。

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

通知记录接口面向前端列表、筛选、详情、统计卡片和人工核查闭环。APP 表示通知中心本地记录，状态为 `SENT`；SMS 尚未接入供应商，只生成 `PENDING` 占位记录；DINGTALK 表示钉钉真实投递结果。

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/notifications?page=1&pageSize=50&alertId=&deviceId=&channel=&status=&auditStatus=` | 消防人员及以上 | 分页筛选通知 |
| GET | `/api/notifications/{id}` | 消防人员及以上 | 通知详情 |
| GET | `/api/notifications/summary` | 消防人员及以上 | 通道、投递、核查及异常待办汇总 |
| POST | `/api/notifications/{id}/audit` | 消防人员及以上 | 提交一次性核查结论并记录操作人 |

筛选值：

- `channel`：`APP`、`SMS`、`DINGTALK`
- `status`：`PENDING`、`SENT`、`FAILED`
- `auditStatus`：`PENDING`、`COMPLETED`

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
  "auditStatus": "PENDING",
  "auditResult": null,
  "auditorUsername": null,
  "auditRemark": null,
  "auditedAt": null,
  "createdAt": "2026-08-24T14:30:00"
}
```

核查请求：

```json
{"result":"FOLLOWED_UP","remark":"已核对接收配置并通知值班人员处理"}
```

`result` 仅支持 `NORMAL`（核查正常）和 `FOLLOWED_UP`（已跟进处理），`remark` 必填且最多 500 字。后端从 JWT 写入 `auditorUsername` 和 `auditedAt`；已完成核查时再次提交返回 `409`，前端应刷新详情，不能覆盖原结论。核查完成仅代表工作人员已检查投递日志，不代表短信或钉钉接收人已读。

摘要 `data`：

```json
{"total":20,"appCount":9,"smsCount":9,"dingTalkCount":2,"pendingCount":9,"sentCount":10,"failedCount":1,"pendingAuditCount":4,"completedAuditCount":16,"attentionCount":1}
```

`attentionCount` 只统计“投递失败且尚未核查”的记录。当前主前端一次加载最近 200 条供本地组合筛选和 CSV 导出；顶部摘要始终统计数据库中的全部记录。

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

状态请求只接受 `{"status":1}` 或 `{"status":2}`，分别表示成功、失败；`0` 是系统创建记录时使用的待下发状态，不能通过该接口写入。创建时先持久化记录；当 `broadcast=DINGTALK_SINGLE_CHAT` 时，后端会立即向全部启用且已绑定的钉钉员工投递并更新结果，`/deliver` 可再次下发。当能力为 `PERSISTENCE_ONLY` 时只保存记录。MQTT 设备下行尚未实现，钉钉成功也不代表烟感设备已经收到或播放。

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

结构化字段存在时，前端在右下角白色小窗口内按安全处置卡片展示；字段缺失时仍可直接展示 `answer`。桌面端窗口最大约 `440 × 620 px`，移动端按视口自适应；请求期间禁止重复提交。问答可使用本地知识检索并通过 Ollama 调用 `gpt-oss:120b-cloud`，不可用时返回内置安全规则答案；`knowledgeBase=CONNECTED` 只代表 RAG 服务可达，只有单次回答的 `source=OLLAMA` 能表明该回答使用了模型。当前后端回退答案对“阈值”的说明仍误称可设置任意正整数，客户端应以设备接口强制的 `100 ppm` 为准。不得将回答作为真实视觉识别结论。详见 [智能问答优化记录](SMART_QA_OPTIMIZATION.md)。

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
7. 验收多角色时不要在同一浏览器普通窗口的多个页签登录不同账号；当前 `localStorage` 会共享并覆盖会话。

开发环境还可打开 `http://127.0.0.1:8080/swagger-ui.html` 查看自动生成的 OpenAPI 文档。

Cloudflare Pages 与本机后端联调、CORS 和断连排查见 [CLOUDFLARE_PAGES.md](CLOUDFLARE_PAGES.md)。
