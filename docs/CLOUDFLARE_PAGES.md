# Cloudflare Pages 与本机后端联调

更新日期：2026-08-26。

本文描述当前常用的演示方案：Vue 前端部署到 Cloudflare Pages，Spring Boot 与 MySQL 继续运行在开发者电脑上，再通过 Cloudflare Tunnel 暴露 HTTPS 后端。

这适合临时演示，不属于稳定生产架构。电脑关机、后端退出、网络中断或 Quick Tunnel 失效都会让前端显示“后端断开连接”。

## Pages 构建配置

| 配置项 | 值 |
| --- | --- |
| 生产分支 | `master` |
| 根目录 | `smoke-detector-frontend` |
| 框架预设 | Vue |
| 构建命令 | `npm run build` |
| 构建输出目录 | `dist` |
| 生产变量 | `VITE_API_BASE=https://<后端公网域名>` |

`VITE_API_BASE` 是 Vite 的**构建时变量**。保存变量不会改变已经发布的 JavaScript，必须再执行一次生产部署。变量值不要以 `/` 结尾。

## 启动本机后端

先确认 MySQL 和 Spring Boot 正常：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

返回 `data.status=UP`、`data.database=UP` 后再启动隧道。后端 CORS 必须包含 Pages 来源：

```text
CORS_ALLOWED_ORIGINS=https://easterproject.pages.dev
```

多个来源使用逗号分隔，来源不带末尾 `/`。

## 启动 Quick Tunnel

```powershell
cloudflared tunnel --url http://127.0.0.1:8080 --no-autoupdate
```

终端会输出类似 `https://<随机名称>.trycloudflare.com` 的地址。等待日志出现 `Registered tunnel connection`，再打开：

```text
https://<随机名称>.trycloudflare.com/api/health
```

确认公网健康接口为 `UP` 后，把该域名写入 Pages 的 `VITE_API_BASE`，保存并重新部署生产环境。

Quick Tunnel 没有可用性保证，每次重启通常会生成新域名。旧域名 DNS 失效后，前端即使部署成功也会显示后端断开。

## 更新前端连接地址

1. 进入 Cloudflare Pages 项目设置中的变量/机密配置。
2. 在 Production 环境设置 `VITE_API_BASE` 为当前隧道 HTTPS 地址。
3. 保存变量。
4. 回到部署列表，对最新 `master` 提交执行重新部署。
5. 等待绿色成功状态后，在浏览器按 `Ctrl + F5` 强制刷新。

只看到“部署成功”还不代表连接地址正确。部署必须发生在环境变量更新之后；否则新的构建仍会嵌入旧隧道地址。

## 断连排查顺序

1. **本机后端**：访问 `http://127.0.0.1:8080/api/health`。失败表示 Java 或 MySQL 有问题。
2. **公网隧道**：访问当前 `trycloudflare.com/api/health`。本机正常但公网失败，表示隧道或域名失效。
3. **Pages 构建变量**：确认生产环境 `VITE_API_BASE` 是当前域名，并且修改变量后重新部署过。
4. **CORS**：确认后端 `CORS_ALLOWED_ORIGINS` 包含 Pages 的精确来源。
5. **浏览器缓存**：按 `Ctrl + F5`，并在开发者工具 Network 中查看 `/api/health` 实际请求域名。

`GET /api/system/capabilities` 返回 `mqtt=CONNECTED` 只代表后端连接到 MQTT Broker；硬件是否在线要看设备最后心跳和数据库最新数据时间。

## 稳定部署建议

长期使用时不要依赖 Quick Tunnel。推荐二选一：

- 使用 Cloudflare 账户创建 Named Tunnel，并绑定固定自定义域名；本机仍需长期在线。
- 把后端和 MySQL 部署到长期运行的云服务器或容器平台，再使用固定 HTTPS API 域名。

无论选择哪种方式，都应配置进程守护、数据库备份、强 JWT、独立数据库账号、设备凭据、日志轮转和监控告警。
