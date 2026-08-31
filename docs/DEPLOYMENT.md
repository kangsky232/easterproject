# 部署说明（上线前准备）

更新日期：2026-08-31。

> 当前仓库已提供生产配置、容器编排和 HTTPS 反向代理模板。演示环境使用 Cloudflare Pages + 本机 Named Tunnel，前端为 [https://easterproject.pages.dev](https://easterproject.pages.dev)，后端固定域名为 [https://api.kangroom.eu.cc](https://api.kangroom.eu.cc)。固定域名解决了重启后地址变化问题，但本机仍是单点，不能视为正式生产上线。

## 已完成的生产化约束

- `prod` 配置启动时会拒绝弱 JWT、空数据库密码、`root` 数据库账户、开发 CORS 来源和开启的 Swagger。
- 设备遥测、心跳在生产环境必须携带 `X-Device-Token`；数据库仅保存令牌摘要。
- MySQL、EMQX、RAG 和后端只加入内部 Docker 网络，不映射宿主机端口。
- 静态前端通过内部 Nginx 反向代理 `/api` 到后端；公网只需要暴露前端入口。
- API 审计日志不会写入请求体、JWT 或设备令牌。可通过 `LOGIN_RATE_LIMIT_ENABLED=true` 在单实例中按客户端地址启用登录失败限流；容器网络中仅信任内部前端代理传递的客户端地址。

## 部署前仍需确定

以下内容必须由实际部署方提供，不能在代码仓库中替代：长期运行服务器地址、生产域名与 DNS 策略、TLS/WAF 策略、强密码/密钥、备份存储位置、告警通知供应商账号、MQTT 认证策略和运维值班联系人。当前 `api.kangroom.eu.cc` 是演示入口，不代表后端已经具备生产可用性。

## 上线门槛

- 后端完整测试和前端生产构建通过，并记录待发布版本。
- `.env` 中不存在 `REPLACE_*`、开发域名、空数据库密码或弱 JWT 密钥。
- 至少保留一个已验证可登录的系统管理员，且 `BOOTSTRAP_ADMIN_ENABLED=false`。
- 完成 MySQL 备份和恢复演练，明确日志轮转、监控和告警接收人。
- 真实 SMS、APP 推送、MQTT 或视觉服务未接入时，在产品界面明确标注不可用或模拟状态。
- RAG 镜像当前使用 Flask 开发服务器；正式部署前改为 Gunicorn 等生产 WSGI 服务，并配置并发、超时、优雅退出和健康检查。
- 使用 HTTPS 域名验证登录、改密后旧 Token 失效、角色权限、设备令牌、告警全流程和错误响应。
- 已有数据库按版本依次执行 `docs/migrations/20260826_decimal_concentration.sql`、`docs/migrations/20260826_extended_sensor_metrics.sql`、`docs/migrations/20260828_role_workspace_3d_map.sql`、`docs/migrations/20260831_hazard_workflow.sql` 和 `docs/migrations/20260831_notification_audit.sql`，并验证小数与扩展遥测、趋势聚合、告警、角色工作区、3D 地图位置、隐患闭环及通知核查字段。

## Cloudflare Pages 演示部署

Pages 配置：根目录 `smoke-detector-frontend`、构建命令 `npm run build`、输出目录 `dist`、生产分支 `master`。生产变量 `VITE_API_BASE` 必须指向当前 HTTPS 后端。

当前生产变量应设置为 `VITE_API_BASE=https://api.kangroom.eu.cc`。该变量在构建时写入静态 JavaScript；只有后端域名发生变化时才需要修改变量并重新部署。正常重启 Spring Boot 或 Named Tunnel 不会改变域名，也不需要重建前端。完整步骤与排错见 [Cloudflare Pages 与本机后端联调](CLOUDFLARE_PAGES.md)。

## 单机 Docker 部署步骤

1. 在 Linux 服务器安装 Docker Engine 与 Compose 插件，并仅在防火墙开放 `80/443`。不要开放 `3306`、`1883`、`5001`、`8080` 或 EMQX 控制台端口。
2. 复制 `.env.example` 为 `.env`，替换所有 `REPLACE_*`，并将 `CORS_ALLOWED_ORIGINS` 改为实际 HTTPS 域名。
3. 首次创建管理员时可暂时设置 `BOOTSTRAP_ADMIN_ENABLED=true`，设置至少 12 位的强管理员密码；管理员创建后改回 `false` 并重启。
4. 构建并启动：`docker compose --env-file .env up -d --build`。
5. 将 `deploy/nginx/smart-smoke-https.conf.example` 安装到宿主机 Nginx，替换域名、证书路径和内部端口。证书可使用企业证书或 ACME 工具申请。
6. 在内部主机验证 `curl http://127.0.0.1:8088/api/health`，再通过 HTTPS 域名验证登录、设备上报、告警和智能问答。

## 设备投产顺序

1. 管理员绑定设备并安全保存响应中的一次性 `deviceAccessToken`。
2. 将令牌写入硬件安全存储或受控配置，不要写入固件仓库、日志或截图。
3. 为每台设备配置 `X-Device-Token` 请求头后，依次验证心跳和遥测上报。
4. 令牌遗失、设备转移或疑似泄露时，立即调用凭据轮换接口并更新硬件配置。

## 运维要求

- 每日至少备份一次 MySQL，并定期恢复演练；`mysql-data` Docker 卷不是备份。
- 收集并轮转 `backend-logs` 卷中的审计与错误日志，避免磁盘写满。
- 在公网入口实施 WAF/请求限流；应用内登录限流仅适用于单实例，扩容后需使用网关或 Redis 共享限流状态。
- 使用华为云 IoTDA MQTT 时，通过机密管理注入 `MQTT_ACCESS_KEY`、`MQTT_ACCESS_CODE` 和可选的 `MQTT_INSTANCE_ID`，不得写入镜像、Git、日志或截图。`mqtt=CONNECTED` 只表示订阅连接正常，仍需监控设备最后心跳和消息滞后。
- 当前 APP 仅代表本地通知中心记录，SMS 保持 `PENDING` 且不会实际发送，视觉复核为规则辅助；接入真实短信、推送、摄像头或模型服务前，不能把它们当成生产告警通道或视觉识别结果。

## 回滚与升级

先在测试环境使用同一份配置完成数据库迁移与回归测试。生产升级前备份数据库，记录镜像版本。本版本将 `smoke_data.concentration` 和 `alert_record.concentration` 升级为 `DECIMAL(12,2)`；旧应用回滚前必须验证其对 DECIMAL 的读取兼容性。禁止直接删除生产表或数据。
