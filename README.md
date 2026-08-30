# 智慧烟感监测系统

面向社区烟感监测场景的全栈项目，包含 Vue 3 管理大屏、Spring Boot API、MySQL 数据存储、HTTP 设备接口、华为云 IoTDA MQTT 数据接入，以及可选的 RAG 和视觉复核扩展。

> 当前版本已实现 MQTT 入站遥测、六类传感器阈值告警、钉钉自动告警、机器人单聊广播、四角色差异化工作台和数据库驱动的模拟 3D 社区地图。真实短信、MQTT 设备广播和摄像头识别仍属于外部集成项，详见 [功能状态](docs/PROJECT_STATUS.md)。

## 当前业务场景

系统当前用于住宅、老旧小区和出租屋的火灾风险监测演示：设备上报烟雾、温湿度、电流、线缆温度和 CO 数据，后端按预警/危险规则自动建警并发送钉钉单聊，物业或社区值班人员在 Web 端完成确认、复核、解决或误报归档。MQ-2 的 ppm 为近似估算，本系统当前适合演示和小范围试点验证，不能替代经过认证的消防报警设施。

居民、消防员、小区管理员和系统管理员登录后会获得不同的导航、工作台说明与操作入口；后端仍是权限最终边界。3D 地图显示模拟楼栋、楼层、房间及设备状态，小区管理员和系统管理员可修改设备位置并同步写入 MySQL。

完整业务边界、告警口径和分阶段迭代计划见 [业务场景与迭代路线](docs/BUSINESS_SCENARIO_AND_ROADMAP.md)。

当前演示前端为 `https://easterproject.pages.dev`，公网 API 固定为 `https://api.kangroom.eu.cc`。API 通过 Cloudflare Named Tunnel 转发到本机 `127.0.0.1:8080`；重启服务无需更换域名，但本机后端、MySQL 和隧道进程必须保持运行。

## 项目结构

```text
smart-smoke/
├── smoke-detector-frontend/  # Vue 3 + Vite + TypeScript + Pinia + ECharts
├── backend/                  # Spring Boot 3 + MyBatis-Plus + JWT
├── rag-service/              # 可选的知识检索与 Ollama 问答服务
├── ai-vision/                # 视觉复核扩展目录
├── hardware/                 # 设备协议与硬件说明
├── deploy/                   # Nginx 等部署模板
├── docs/                     # API、开发、部署和功能状态文档
└── docker-compose.yml        # 可选的生产容器编排模板
```

## 本地快速启动（无需 Docker）

前置环境：JDK 17+、Maven 3.9+、Node.js 18+、MySQL 8。

1. 启动本机 MySQL，创建 `smart_smoke` 数据库并执行 `docs/schema.sql`。已有旧数据库还需执行 [浓度小数迁移](docs/migrations/20260826_decimal_concentration.sql)。
2. 启动后端：

```powershell
cd backend
mvn spring-boot:run
```

3. 启动前端：

```powershell
cd smoke-detector-frontend
npm install
npm run dev
```

默认访问地址：

- 前端：`http://127.0.0.1:5173`（端口占用时 Vite 会选择下一个端口）
- 后端：`http://127.0.0.1:8080`
- Swagger：`http://127.0.0.1:8080/swagger-ui.html`
- 独立用户管理页：`http://127.0.0.1:8080/admin/`

MQTT 和 RAG 服务不是本地核心功能启动的前置条件。配置 `MQTT_ENABLED=true` 后，后端会订阅华为云转发主题，接收烟雾浓度、温湿度、电流、线缆温度、CO 值和蜂鸣器状态，数值统一保留两位小数；RAG 不可用时后端会返回内置安全规则答案；未接短信供应商时 SMS 通知保留为待发送记录。

配置钉钉 Client ID/Client Secret 并启用 `DINGTALK_ENABLED` 后，后端通过 Stream 模式接收机器人私聊。员工首次私聊会自动绑定；网页广播以及烟雾、温湿度、电流、线缆温度和 CO 自动告警会发送到这些员工的钉钉单聊。可使用 `.\scripts\start-backend.ps1` 加载本机的 `.env.dingtalk.local` 并启动后端。

管理大屏默认以“实时”模式展示最近 120 条原始浓度数据，每 3 秒刷新一次，与当前华为云设备上报周期保持一致；24 小时、7 天和 30 天视图使用后端聚合趋势接口。MQTT 消息到达后端时会立即入库，不等待轮询。

## 开发账号说明

全新开发数据库会按 `application-dev.yml` 的引导配置创建管理员；已有数据库不会自动覆盖已有账号密码。团队联调时应由负责人分发开发账号，或通过 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 创建单独的本地管理员，不要在代码和文档中提交真实密码。

## 验证

```powershell
# 后端
cd backend
mvn test

# 前端（包含 TypeScript 检查）
cd smoke-detector-frontend
npm run build
```

## 文档

- [文档索引](docs/README.md)
- [本地开发说明](docs/DEVELOPMENT.md)
- [后端 API](docs/API.md)
- [前端接口协作说明](docs/FRONTEND_API.md)
- [业务场景与迭代路线](docs/BUSINESS_SCENARIO_AND_ROADMAP.md)
- [功能完成状态](docs/PROJECT_STATUS.md)
- [智能问答优化记录](docs/SMART_QA_OPTIMIZATION.md)
- [部署与上线检查](docs/DEPLOYMENT.md)
- [Cloudflare Pages 与本机后端联调](docs/CLOUDFLARE_PAGES.md)
- [硬件与华为云 MQTT 接入](hardware/README.md)

`docker-compose.yml` 用于可选的服务器容器化部署，不是本地启动后端、前端或接入 MQTT 的强制要求。
