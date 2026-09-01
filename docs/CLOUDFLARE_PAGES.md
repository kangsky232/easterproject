# Cloudflare Pages 与本机后端联调

更新日期：2026-09-01。

当前演示架构为：Vue 前端部署到 Cloudflare Pages，Spring Boot 与 MySQL 运行在开发者电脑上，Cloudflare Named Tunnel 使用固定域名把公网 HTTPS 请求转发到本机后端。

| 组件 | 当前地址 |
| --- | --- |
| Web 前端 | [https://easterproject.pages.dev](https://easterproject.pages.dev) |
| 公网后端 | [https://api.kangroom.eu.cc](https://api.kangroom.eu.cc) |
| 本机后端 | `http://127.0.0.1:8080` |

Named Tunnel 重启后不会产生新地址，因此正常重启服务无需修改 Pages 环境变量。不过电脑关机、MySQL/后端退出、网络中断或 `cloudflared` 停止，仍会让前端显示“后端断开连接”。这是一套固定地址的演示架构，不是高可用生产部署。

2026-09-01 本机配置中，MQTT 和钉钉已启用，视觉巡检已实现但未配置 DeepSeek Key，因此视觉能力预期为 `SIMULATION_FALLBACK`。Pages、公网健康、后端/MySQL 与隧道在线状态会变化，应在每次演示前重新验证。

## Pages 构建配置

| 配置项 | 值 |
| --- | --- |
| 生产分支 | `master` |
| 根目录 | `smoke-detector-frontend` |
| 框架预设 | Vue |
| 构建命令 | `npm run build` |
| 构建输出目录 | `dist` |
| 生产变量 | `VITE_API_BASE=https://api.kangroom.eu.cc` |

`VITE_API_BASE` 是 Vite 的构建时变量，值不要以 `/` 结尾。只有该地址发生变化时，才需要保存新值并重新执行生产部署；重启本机服务不会改变该值。

## 启动顺序

### 1. 启动 MySQL 与后端

在仓库根目录运行：

```powershell
$env:CORS_ALLOWED_ORIGINS = 'https://easterproject.pages.dev'
.\scripts\start-backend.ps1
```

该脚本会加载 Git 忽略的 `.env.mqtt.local`、`.env.dingtalk.local` 和 `.env.vision.local`，再启动 Spring Boot；上面的 CORS 变量需在同一个 PowerShell 进程中先设置，或由 IDE/进程管理器注入。要启用 DeepSeek，在 `.env.vision.local` 中设置 `DEEPSEEK_API_KEY`。不要把真实 Client Secret、DeepSeek Key 或 MQTT 凭据提交到 Git。

验证本机接口：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

确认 `data.status=UP`、`data.database=UP` 后再启动隧道。后端 CORS 必须包含 Pages 精确来源：

```text
CORS_ALLOWED_ORIGINS=https://easterproject.pages.dev
```

### 2. 启动 Named Tunnel

使用创建该域名映射时配置的 Named Tunnel 名称或 UUID：

```powershell
cloudflared tunnel run <TUNNEL_NAME_OR_UUID>
```

Cloudflare Tunnel 配置中的 ingress 应把 `api.kangroom.eu.cc` 指向 `http://127.0.0.1:8080`。隧道凭据属于本机机密，不应提交到仓库。

验证公网接口：

```powershell
Invoke-RestMethod https://api.kangroom.eu.cc/api/health
```

返回 `data.status=UP` 后，Pages 前端即可连接后端。

## 什么时候需要重新部署前端

以下情况需要重新部署：

- GitHub `master` 有新的前端代码提交。
- Cloudflare Pages 的 `VITE_API_BASE`、构建命令或其他构建时变量发生变化。

以下情况不需要重新部署：

- 仅重启 Spring Boot、MySQL 或 `cloudflared`。
- Named Tunnel 保持绑定 `api.kangroom.eu.cc`，只是短暂断线后重连。
- 仅新增钉钉接收人或修改本机未提交的机密配置。
- 仅设置或轮换 `DEEPSEEK_API_KEY`；这是后端运行时变量，不需要重建静态前端。

## 断连排查顺序

1. **本机后端**：访问 `http://127.0.0.1:8080/api/health`。失败时检查 MySQL、Java 进程和后端日志。
2. **公网隧道**：访问 `https://api.kangroom.eu.cc/api/health`。本机正常但公网返回 `502 Bad Gateway` 时，优先检查 `cloudflared` 是否运行及 ingress 是否仍指向 `127.0.0.1:8080`。
3. **Pages 构建变量**：确认 Production 的 `VITE_API_BASE` 为 `https://api.kangroom.eu.cc`，变量值没有末尾 `/`。
4. **CORS**：确认后端 `CORS_ALLOWED_ORIGINS` 包含 `https://easterproject.pages.dev`。
5. **浏览器缓存**：按 `Ctrl + F5`，在开发者工具 Network 中确认 `/api/health` 请求目标确实是 `api.kangroom.eu.cc`。

`GET /api/system/capabilities` 返回 `mqtt=CONNECTED` 只说明后端连接到 MQTT Broker；硬件是否在线仍要看设备最后心跳和最新遥测时间。

## Quick Tunnel 仅作应急备用

Named Tunnel 暂时不可用时，可临时运行：

```powershell
cloudflared tunnel --url http://127.0.0.1:8080 --no-autoupdate
```

Quick Tunnel 会生成随机 `trycloudflare.com` 地址，通常在重启后改变。若临时切换到该地址，必须修改 Pages 的 `VITE_API_BASE` 并重新部署；恢复 Named Tunnel 后也要改回固定域名。因此不建议把 Quick Tunnel 作为日常启动方式。

## 生产化建议

长期运行时，应把后端和 MySQL 迁移到云服务器或受管容器/数据库平台，并配置进程守护、最小权限、数据库备份、日志轮转、监控告警、WAF 和恢复演练。Named Tunnel 可以继续作为入口，但不能用它替代应用与数据库的高可用设计。详细阶段计划见 [业务场景与迭代路线](BUSINESS_SCENARIO_AND_ROADMAP.md)。
