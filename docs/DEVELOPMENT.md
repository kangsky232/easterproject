# 本地开发说明

更新日期：2026-08-24。本说明以 Windows PowerShell 为例，默认采用非 Docker 启动方式。

## 组件与依赖

| 组件 | 默认端口 | 本地核心联调是否必需 | 说明 |
| --- | ---: | --- | --- |
| MySQL 8 | `3306` | 是 | 保存用户、设备、告警和监测数据 |
| Spring Boot 后端 | `8080` | 是 | API、认证和独立用户管理页 |
| Vue/Vite 前端 | `5173` | 是 | 端口占用时 Vite 会自动选择下一个端口 |
| RAG 服务 | `5001` | 否 | 不可用时后端使用内置安全规则降级 |
| MQTT Broker | `1883` | 否 | 仅真实设备消息和广播发布需要 |

需要安装 JDK 17+、Maven 3.9+、Node.js 18+、npm 和 MySQL 8。

## 1. 初始化 MySQL

启动本机 MySQL 后，创建 `smart_smoke` 数据库并执行 `docs/schema.sql`。开发配置默认连接：

```text
jdbc:mysql://127.0.0.1:3306/smart_smoke
username=root
password=<空>
```

如果你的 MySQL 账号或端口不同，请设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，不要修改并提交个人密码。

## 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

验证：

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/api/health
```

开发环境 Swagger：`http://127.0.0.1:8080/swagger-ui.html`；独立用户管理页：`http://127.0.0.1:8080/admin/`。

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

MQTT Broker 不影响登录、数据库接口和前端基本联调。需要接入真实 MQTT 时，可使用本机已安装的 Broker、远程 Broker 或 Docker 中的 EMQX，并通过 `MQTT_BROKER` 指向实际地址。

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
| `MQTT_BROKER` | MQTT 地址 | `tcp://localhost:1883` |
| `DEVICE_AUTH_ENABLED` | 开发环境设备令牌校验 | 默认 `false`；生产强制开启 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端来源 | 开发默认允许 `5173/5174` |

## 设备接入

生产配置强制要求 `X-Device-Token`。绑定设备或轮换凭据时，明文令牌只返回一次，服务端仅保存 SHA-256 摘要。

```http
POST /api/telemetry
X-Device-Token: <deviceAccessToken>
Content-Type: application/json

{"deviceId":"SMOKE-001","concentration":850,"messageId":"SMOKE-001-001"}
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
- 修改密码后出现 `401`：旧 JWT 会立即失效，使用新密码重新登录即可。
