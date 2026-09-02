# 智慧烟感系统部署文档

更新日期：2026-09-02。

本文提供两条部署路线：

- **当前演示环境**：Vue 前端放在 Cloudflare Pages，Spring Boot、MySQL 和 Cloudflare Tunnel 运行在一台 Windows 电脑上。适合演示和联调，步骤最少。
- **正式服务器环境**：使用 Docker Compose 运行前端、后端、MySQL、EMQX 和 RAG 服务。适合继续生产化，但仍需补齐 HTTPS、备份、监控和高可用。

## 1. 当前演示环境

| 组件 | 当前地址或状态 |
| --- | --- |
| GitHub 仓库 | `https://github.com/kangsky232/easterproject`，生产分支 `master` |
| Web 前端 | [https://easterproject.pages.dev](https://easterproject.pages.dev) |
| 公网 API | [https://api.kangroom.eu.cc](https://api.kangroom.eu.cc) |
| 本机 API | `http://127.0.0.1:8080` |
| MySQL | 本机 `127.0.0.1:3306/smart_smoke` |
| Named Tunnel | `easter-backend` |
| AI 图片分析 | 已配置 DeepSeek Vision；输入仍是 15 张模拟轮播图片 |
| 钉钉 | 企业内部机器人单聊，接收人需先私聊机器人完成绑定 |
| MQTT | 当前接入华为云 IoTDA |
| 智能问答知识服务 | 当前允许使用后端安全规则降级 |

当前状态是运行快照，不是永久在线承诺。电脑关机、MySQL/后端退出、网络中断或 `cloudflared` 停止，都会让前端显示“后端连接中断”。

## 2. 演示环境前置条件

部署电脑需要安装：

- Git
- JDK 17 或更高版本
- Maven 3.9 或更高版本
- Node.js 18 或更高版本及 npm
- MySQL 8
- `cloudflared`

还需要：

- 一个 Cloudflare 账号和已接入 Cloudflare 的域名
- 一个 Cloudflare Pages 项目
- DeepSeek API Key（可选；不配置时明确使用模拟规则降级）
- 钉钉企业内部应用机器人凭据（可选）
- MQTT 平台凭据（可选）

## 3. 获取代码并初始化数据库

```powershell
git clone https://github.com/kangsky232/easterproject.git
cd easterproject
```

创建数据库：

```sql
CREATE DATABASE smart_smoke
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

全新数据库执行 [schema.sql](schema.sql)。使用 MySQL 命令行时可运行：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 smart_smoke
```

进入 MySQL 后执行：

```sql
SOURCE docs/schema.sql;
```

已有数据库应按文件名时间顺序执行 `docs/migrations/` 下的迁移：

1. `20260826_decimal_concentration.sql`
2. `20260826_extended_sensor_metrics.sql`
3. `20260828_role_workspace_3d_map.sql`
4. `20260831_hazard_workflow.sql`
5. `20260831_notification_audit.sql`
6. `20260901_vision_patrol.sql`

后端启动时也会兼容补齐部分功能表，但显式执行迁移更便于部署审计和故障定位。

## 4. 配置本机机密

仓库根目录的启动脚本会依次加载以下文件：

- `.env.mqtt.local`
- `.env.dingtalk.local`
- `.env.vision.local`

这些文件已被 `.gitignore` 忽略，不应提交到 GitHub。不要把真实密钥写入 README、源码、截图或前端环境变量。

### 4.1 MySQL、CORS 与 MQTT

当前脚本会加载 `.env.mqtt.local`，因此演示环境可以把后端联调变量一并放在这里：

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/smart_smoke?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=

CORS_ALLOWED_ORIGINS=https://easterproject.pages.dev
LOGIN_RATE_LIMIT_ENABLED=false

MQTT_ENABLED=false
MQTT_BROKER=ssl://example-mqtt-host:8883
MQTT_ACCESS_KEY=
MQTT_ACCESS_CODE=
MQTT_INSTANCE_ID=
MQTT_TOPIC=smoke/report
```

如果 MySQL 使用密码，请填写 `DB_PASSWORD`。生产环境不要使用 `root` 或空密码。

### 4.2 钉钉

`.env.dingtalk.local` 示例：

```dotenv
DINGTALK_ENABLED=true
DINGTALK_CLIENT_ID=你的ClientId
DINGTALK_CLIENT_SECRET=你的ClientSecret
DINGTALK_ROBOT_CODE=
```

`DINGTALK_ROBOT_CODE` 留空时默认使用 Client ID。每名接收人需要先在钉钉中私聊机器人一次，收到“连接成功”后才会写入接收人表。

### 4.3 DeepSeek 图片分析

`.env.vision.local` 示例：

```dotenv
VISION_ENABLED=true
DEEPSEEK_API_KEY=你的DeepSeek密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_VISION_MODEL=deepseek-v4-flash-vision-exp
VISION_FRAME_BASE_URL=https://easterproject.pages.dev
VISION_INTERVAL_MS=15000
VISION_INITIAL_DELAY_MS=3000
VISION_TIMEOUT_SECONDS=45
VISION_CONFIDENCE_THRESHOLD=0.65
```

后端能力接口返回 `visualAi=DEEPSEEK_VISION` 只证明密钥配置已加载。需要在页面点击“开始巡检”，并看到一次成功分析结果，才能证明密钥、模型权限和额度均可用。

## 5. 启动本机后端

在仓库根目录打开第一个 PowerShell 窗口：

```powershell
.\scripts\start-backend.ps1
```

此窗口必须保持运行。脚本会加载上述三个本机配置文件并执行 Maven 启动。

验证本机后端：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/system/capabilities
```

健康接口至少应返回：

```text
status=UP
database=UP
```

如果 MySQL 未启动或连接配置错误，应先解决数据库问题，再启动隧道。

## 6. 启动固定域名 Tunnel

当前电脑已经创建了名为 `easter-backend` 的 Named Tunnel。打开第二个 PowerShell 窗口运行：

```powershell
cloudflared tunnel run --url http://127.0.0.1:8080 easter-backend
```

此窗口同样必须保持运行。

> 当前电脑没有依赖本地 `config.yml` 的 ingress 规则，因此 `--url http://127.0.0.1:8080` 不能省略。只运行 `cloudflared tunnel run easter-backend` 会连接到 Cloudflare，但对外请求返回 `503`。

验证公网后端：

```powershell
Invoke-RestMethod https://api.kangroom.eu.cc/api/health
Invoke-RestMethod https://api.kangroom.eu.cc/api/system/capabilities
```

在新的 Cloudflare 账号中首次创建固定隧道时，可参考：

```powershell
cloudflared tunnel login
cloudflared tunnel create smoke-backend
cloudflared tunnel route dns smoke-backend api.example.com
cloudflared tunnel run --url http://127.0.0.1:8080 smoke-backend
```

然后将下面所有 `api.kangroom.eu.cc` 替换为自己的 API 域名。

## 7. 部署 Cloudflare Pages 前端

### 7.1 GitHub 自动部署

在 Cloudflare Pages 中连接 GitHub 仓库并设置：

| 配置项 | 值 |
| --- | --- |
| 生产分支 | `master` |
| 根目录 | `smoke-detector-frontend` |
| 框架预设 | Vue |
| 构建命令 | `npm run build` |
| 输出目录 | `dist` |
| 生产变量 | `VITE_API_BASE=https://api.kangroom.eu.cc` |

`VITE_API_BASE` 不要带末尾 `/`。它会在构建时写进静态 JavaScript，保存或修改后必须重新部署。

完成配置后，推送 `master` 即可触发 Pages 构建：

```powershell
git push origin master
```

### 7.2 Wrangler 手工部署

GitHub 暂时不可用时，可直接部署已经构建的前端：

```powershell
cd smoke-detector-frontend
$env:VITE_API_BASE = 'https://api.kangroom.eu.cc'
npm.cmd ci
npm.cmd run build
npx.cmd --yes wrangler@latest pages deploy dist --project-name easterproject --branch master
Remove-Item Env:VITE_API_BASE
```

**不要漏掉 `VITE_API_BASE`。** 如果直接执行默认 `npm run build` 后把产物部署到 Pages，线上页面会把 `/api` 请求发给静态站点自身，并显示“后端连接中断”，即使真正的后端和隧道都正常。

## 8. 每次演示的启动顺序

前端部署在 Pages 后不需要本地启动 Vite。每次电脑重启后按以下顺序恢复服务：

1. 启动 MySQL。
2. 在仓库根目录运行 `.\scripts\start-backend.ps1`。
3. 确认本机 `/api/health` 返回 `UP`。
4. 运行 `cloudflared tunnel run --url http://127.0.0.1:8080 easter-backend`。
5. 确认公网 `/api/health` 返回 `UP`。
6. 打开 [https://easterproject.pages.dev](https://easterproject.pages.dev)，必要时按 `Ctrl+F5`。

DeepSeek 自动巡检在后端重启后默认暂停，需要有视觉复核权限的账号点击“开始巡检”。点击后立即分析一帧，并每 15 秒继续分析，直到点击暂停。

## 9. 验收清单

部署完成后至少检查：

- `GET /api/health`：本机和公网均为 `UP`，数据库为 `UP`。
- `GET /api/system/capabilities`：确认 MQTT、视觉 AI、知识服务和钉钉通道状态符合预期。
- 使用系统管理员账号登录，确认首页、数据总览、社区三维态势、隐患管理、通知审计和用户管理可访问。
- 使用不同角色账号检查前后端权限边界。
- 点击视觉巡检“开始”，确认模型名称、分析结果和错误提示正确。
- 钉钉绑定用户后发送一条广播或产生一个新告警，确认手机端收到单聊。
- 在 3D 地图确认 501 设备显示在 5 层。
- 前端生产构建通过：`npm.cmd run build`。
- 后端测试通过：`mvn.cmd test`。

视觉巡检对同一机位已有“待人工判断”事件时不会重复建档或重复发送钉钉。要验证再次推送，应先在页面完成该机位旧事件的人工判断。

## 10. 常见故障

| 现象 | 排查与处理 |
| --- | --- |
| 前端显示“后端连接中断” | 依次检查本机健康、公网健康、Pages 包内的 `VITE_API_BASE` 和浏览器缓存。 |
| 本机 `8080` 不通 | 检查 MySQL、Java/Maven 进程和第一个 PowerShell 窗口日志。 |
| 公网返回 Cloudflare `1033` | 没有可用 Tunnel 连接，重新启动 `cloudflared`。 |
| 公网返回 `503` | Named Tunnel 没有入口规则；当前环境应使用带 `--url http://127.0.0.1:8080` 的命令。 |
| 公网 `UP`，浏览器仍失败 | 检查 `CORS_ALLOWED_ORIGINS` 是否精确包含 Pages 来源，且无末尾 `/`。 |
| 前端请求发往 Pages 自身 | 手工构建时漏掉 `VITE_API_BASE`，设置变量后重新构建和部署。 |
| 页面显示 `DEEPSEEK_ERROR` | 检查 Key、模型权限、余额、外部图片 URL 和后端网络；失败帧不会生成告警。 |
| 疑似火情没有新钉钉消息 | 检查接收人绑定、钉钉配置和事件投递字段；同机位已有待判断事件时系统会抑制重复消息。 |
| 修改后页面仍是旧内容 | 等待 Pages 部署完成并按 `Ctrl+F5`；通过 `index.html` 的 JS 文件哈希确认新版本。 |

## 11. 正式服务器 Docker 部署

当前仓库包含 `docker-compose.yml`、前后端 Dockerfile 和 Nginx 配置。推荐在 Linux 服务器上执行：

```bash
cp .env.example .env
# 编辑 .env，替换全部 REPLACE_*，填写实际域名和可选外部服务凭据
docker compose --env-file .env config
docker compose --env-file .env up -d --build
```

关键要求：

- `MYSQL_USER` 使用专用数据库账户，不使用 `root`。
- MySQL 普通账户密码、Root 密码和 JWT 密钥均使用不同的长随机值。
- `CORS_ALLOWED_ORIGINS` 使用实际 HTTPS 前端来源。
- 首次创建管理员时可临时设置 `BOOTSTRAP_ADMIN_ENABLED=true`；创建成功后改回 `false` 并重启。
- 不对公网开放 `3306`、`1883`、`5001`、`8080` 或 EMQX 管理端口。
- Compose 前端通过内部 Nginx 同源转发 `/api`，不需要设置 Pages 使用的 `VITE_API_BASE`。
- 使用 `deploy/nginx/smart-smoke-https.conf.example` 配置 HTTPS 入口，并替换域名和证书路径。

生产配置会拒绝弱 JWT、空数据库密码、`root` 数据库账户、开发 CORS 来源、Swagger 和管理员密码重置开关。

## 12. 安全、备份与回滚

- `.env`、`.env.*.local`、Tunnel 凭据和真实密钥不得提交 Git。
- 提交前运行 `git status --short`，确认没有机密文件进入暂存区。
- 若密钥曾出现在聊天、截图或日志中，应立即在供应商控制台轮换。
- MySQL 至少每日备份，并定期做恢复演练；Docker 卷不是备份。
- 收集并轮转后端日志，监控磁盘、数据库、Tunnel、MQTT、DeepSeek 和钉钉调用失败。
- 升级前备份数据库并记录 Git 提交号；先执行迁移和测试，再切换应用版本。
- 回滚前确认旧版本能够读取当前数据库字段，禁止通过删除表或清空生产数据实现回滚。

当前模拟视觉图片和 DeepSeek 结论仅用于功能演示与人工辅助，不能替代经过认证的消防报警设备、现场核验或 119 报警。
