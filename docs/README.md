# 文档索引

更新日期：2026-08-24。

- [DEVELOPMENT.md](DEVELOPMENT.md)：本地环境、非 Docker 启动、配置、测试与常见问题。
- [API.md](API.md)：后端接口、请求示例、权限和错误处理。
- [FRONTEND_API.md](FRONTEND_API.md)：给前端开发者的稳定接口契约、字段模型和联调清单。
- [PROJECT_STATUS.md](PROJECT_STATUS.md)：已实现、模拟实现和待接入能力的边界。
- [SMART_QA_OPTIMIZATION.md](SMART_QA_OPTIMIZATION.md)：智能问答架构、已完成优化、测试基线和后续路线。
- [DEPLOYMENT.md](DEPLOYMENT.md)：生产配置、容器化、上线检查和运维要求。
- [schema.sql](schema.sql)：MySQL 初始建表脚本。
- [硬件说明](../hardware/README.md)：设备上报方式和 MQTT 主题约定。
- [视觉服务说明](../ai-vision/README.md)：当前占位接口和正式接入条件。

开发环境启动后还可访问动态 OpenAPI 文档：`http://127.0.0.1:8080/swagger-ui.html`。

接口发生变化时，应同时更新 `API.md`、`FRONTEND_API.md` 和相应前端类型定义；部署能力发生变化时，应同步更新 `PROJECT_STATUS.md` 与 `DEPLOYMENT.md`。
