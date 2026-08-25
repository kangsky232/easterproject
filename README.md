# 智慧烟感监测系统

面向社区烟感监测场景的全栈项目，包含 Vue 3 管理大屏、Spring Boot API、MySQL 数据存储、设备上报协议，以及可选的 MQTT、RAG 和视觉复核扩展。

> 当前版本可在不使用 Docker、不启动 MQTT 的情况下完成登录、设备、遥测、告警、通知记录和用户管理联调。真实短信、APP 推送、MQTT 广播和摄像头识别仍属于外部集成项，详见 [功能状态](docs/PROJECT_STATUS.md)。

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

1. 启动本机 MySQL，创建 `smart_smoke` 数据库并执行 `docs/schema.sql`。
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

MQTT 和 RAG 服务不是本地核心功能启动的前置条件。RAG 不可用时后端会返回内置安全规则答案；未接短信供应商时 SMS 通知保留为待发送记录。

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
- [功能完成状态](docs/PROJECT_STATUS.md)
- [智能问答优化记录](docs/SMART_QA_OPTIMIZATION.md)
- [部署与上线检查](docs/DEPLOYMENT.md)

`docker-compose.yml` 用于可选的服务器容器化部署，不是本地启动后端、前端或接入 MQTT 的强制要求。
