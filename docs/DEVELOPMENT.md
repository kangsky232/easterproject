# 本地开发说明

更新日期：2026-08-27。本说明以 Windows PowerShell 为例，默认采用非 Docker 启动方式。

## 组件与依赖

| 组件 | 默认端口 | 本地核心联调是否必需 | 说明 |
| --- | ---: | --- | --- |
| MySQL 8 | `3306` | 是 | 保存用户、设备、告警和监测数据 |
| Spring Boot 后端 | `8080` | 是 | API、认证和备用独立用户管理页 |
| Vue/Vite 前端 | `5173` | 是 | 端口占用时 Vite 会自动选择下一个端口 |
| RAG 服务 | `5001` | 否 | 不可用时后端使用内置安全规则降级 |
| MQTT Broker/华为云 IoTDA | 由平台决定 | 否 | 后端已支持 MQTT 入站遥测；HTTP 联调不依赖它 |
| 钉钉 Stream | 钉钉云端 | 否 | 启用后接收机器人私聊，并将网页广播和自动告警下发到已绑定员工的钉钉单聊 |

需要安装 JDK 17+、Maven 3.9+、Node.js 18+、npm 和 MySQL 8。

## 1. 初始化 MySQL

启动本机 MySQL 后，创建 `smart_smoke` 数据库并执行 `docs/schema.sql`。开发配置默认连接：

```text
jdbc:mysql://127.0.0.1:3306/smart_smoke
username=root
password=<空>
```

如果你的 MySQL 账号或端口不同，请设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，不要修改并提交个人密码。

从旧版本升级时，再执行：

```powershell
mysql -uroot smart_smoke -e "SOURCE docs/migrations/20260826_decimal_concentration.sql"
mysql -uroot smart_smoke -e "SOURCE docs/migrations/20260826_extended_sensor_metrics.sql"
mysql -uroot smart_smoke -e "SOURCE docs/migrations/20260828_role_workspace_3d_map.sql"
```

这些迁移依次补齐数值精度、扩展传感器字段，以及 3D 楼栋/设备位置表。后端启动时 `FeatureSchemaInitializer` 也会兼容补齐地图表并初始化三栋模拟住宅楼；显式执行迁移便于部署审计和版本追踪。

## 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

如需同时加载仓库根目录中被 Git 忽略的 `.env.mqtt.local` 和 `.env.dingtalk.local`，可在仓库根目录运行：

```powershell
.\scripts\start-backend.ps1
```

验证：

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/api/health
```

开发环境 Swagger：`http://127.0.0.1:8080/swagger-ui.html`。用户管理已集成到主前端的系统管理员工作区；备用独立页面为 `http://127.0.0.1:8080/admin/index.html`。

## 3. 启动前端

```powershell
cd smoke-detector-frontend
npm install
npm run dev
```

Vite 会把 `/api` 代理到 `http://127.0.0.1:8080`。浏览器访问终端中显示的地址，通常是 `http://127.0.0.1:5173`。

## 4. 可选服务

RAG 服务：

```powershell
cd rag-service
python -m pip install -r requirements.txt
python app.py
```

MQTT 不影响登录、数据库接口和前端基本联调。需要接入华为云规则转发时，设置 `MQTT_ENABLED=true`、Broker、Access Key、Access Code、可选 Instance ID 和订阅主题。真实值可保存到仓库已忽略的 `.env.mqtt.local`，但 Spring Boot 不会自动读取该文件，启动进程或 IDE 必须显式加载这些环境变量。

订阅器接收 `Smoke_Value`、`Temperature`、`Humidity`、`Current`、`WireTemperature`、`CO_Value` 和 `BeepStatus`，数值按两位小数入库。详细 payload 和配置见 [硬件与 MQTT 说明](../hardware/README.md)。MQTT 设备下行尚未实现；网页广播可通过钉钉机器人发送给手机端员工。

钉钉接入使用 Stream 模式，无需填写公网回调地址。在 `.env.dingtalk.local` 中设置 `DINGTALK_ENABLED=true`、Client ID 和 Client Secret，再用上面的启动脚本运行后端。每名接收人需要先在钉钉中私聊机器人一次；机器人回复“连接成功”后，其员工 userId 会写入 `dingtalk_recipient`，网页广播和新产生的传感器告警会发送到该单聊。真实 Client Secret 不得提交到 Git。

## 开发账号

- 全新数据库会按 `application-dev.yml` 的引导配置创建管理员。
- 已有数据库不会因为配置中的默认值而重置现有密码，因此不要假设任何已有环境都能使用默认账号。
- 推荐通过 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 为每位开发者创建独立账号。
- 仅在本地遗失密码时，才可单次设置 `BOOTSTRAP_ADMIN_RESET_PASSWORD=true`；成功启动后立即移除。生产配置会拒绝该开关。
- 密码、JWT、设备令牌和数据库凭据不得写入源码、截图或文档。

## 常用环境变量

| 变量 | 用途 | 开发默认值/说明 |
| --- | --- | --- |
| `DB_URL` | JDBC 地址 | `jdbc:mysql://127.0.0.1:3306/smart_smoke...` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `root` / 空密码 |
| `JWT_SECRET` | JWT 签名密钥 | 开发有占位值，生产必须替换 |
| `JWT_EXPIRATION` | JWT 有效期（毫秒） | `86400000` |
| `BOOTSTRAP_ADMIN_ENABLED` | 是否创建引导管理员 | 开发默认 `true` |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | 引导管理员凭据 | 建议按开发者单独设置 |
| `BOOTSTRAP_ADMIN_RESET_PASSWORD` | 单次本地密码恢复 | 默认 `false`，生产禁止 |
| `RAG_SERVICE_URL` | RAG 接口 | `http://127.0.0.1:5001/api/chat/query` |
| `MQTT_ENABLED` | 是否启动 MQTT 入站订阅 | 默认 `false` |
| `MQTT_BROKER` | MQTT 地址 | 华为云通常使用 `ssl://...:8883` |
| `MQTT_ACCESS_KEY` / `MQTT_ACCESS_CODE` | MQTT 登录凭据 | 仅放在本地环境或机密管理中 |
| `MQTT_INSTANCE_ID` | MQTT 实例 ID | 平台未要求时可留空 |
| `MQTT_TOPIC` | 规则转发订阅主题 | 默认 `smoke/report` |
| `DINGTALK_ENABLED` | 是否启动钉钉 Stream 和广播下发 | 默认 `false` |
| `DINGTALK_CLIENT_ID` / `DINGTALK_CLIENT_SECRET` | 钉钉企业内部应用凭证 | 仅放在本地环境或机密管理中 |
| `DINGTALK_ROBOT_CODE` | 主动发送消息的机器人编码 | 可留空，默认使用 Client ID |
| `DEVICE_AUTH_ENABLED` | 开发环境设备令牌校验 | 默认 `false`；生产强制开启 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端来源 | 开发默认允许 `5173/5174` |
| `LOGIN_RATE_LIMIT_ENABLED` | 是否启用登录失败限流 | 默认 `true`；生产不应关闭 |

## 设备接入

生产配置强制要求 `X-Device-Token`。绑定设备或轮换凭据时，明文令牌只返回一次，服务端仅保存 SHA-256 摘要。

```http
POST /api/telemetry
X-Device-Token: <deviceAccessToken>
Content-Type: application/json

{"deviceId":"SMOKE-001","concentration":20.37,"messageId":"SMOKE-001-001"}
```

设备重试时必须复用相同 `messageId`，避免重复入库。令牌遗失时由管理员调用 `POST /api/devices/{id}/credentials` 轮换，旧令牌立即失效。

## 测试与构建

```powershell
# 后端完整测试
cd backend
$taskMavenRepo = Join-Path $env:TEMP 'smart-smoke-maven-repository'
mvn "-Dmaven.repo.local=$taskMavenRepo" test

# 前端类型检查与生产构建
cd ../smoke-detector-frontend
npm run build

# RAG 服务语法检查
cd ../rag-service
python -m py_compile app.py
```

## 常见问题

- 后端报 `Communications link failure`：先确认 `127.0.0.1:3306` 正在监听，再检查数据库名和账号密码。
- 登录一直失败：确认前端请求的是当前后端，并查询数据库中的实际账号状态；已有数据库不会自动恢复默认密码。
- 前端启动到 `5174`：说明 `5173` 已被占用，属于 Vite 的正常行为，后端开发 CORS 已允许两个端口。
- MQTT 连接失败：本地核心接口仍可使用；只有真实 MQTT 收发需要启动 Broker。
- 钉钉机器人不回复：确认应用已经发布、消息接收模式为 Stream、环境变量已加载，并在启动日志中查找 `DingTalk Stream listener started`。
- `mqtt=CONNECTED` 但设备离线：这只代表后端连上 Broker；检查硬件供电、IoTDA 规则转发、订阅主题、设备编号和数据库最新时间。
- Pages 显示后端断开：先检查本机 `http://127.0.0.1:8080/api/health`，再检查 Named Tunnel 公网入口 `https://api.kangroom.eu.cc/api/health`。公网返回 `502` 通常表示后端、MySQL 或 `cloudflared` 未正常运行；固定域名未改变时无需重新配置 Pages。详见 [Cloudflare Pages 联调](CLOUDFLARE_PAGES.md)。
- 浓度一直是 `.00`：确认浮点升级后是否收到过新数据；旧记录曾被截断，不能证明硬件原始值是否包含小数。
- 修改密码后出现 `401`：旧 JWT 会立即失效，使用新密码重新登录即可。
