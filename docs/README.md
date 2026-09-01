# 文档索引

更新日期：2026-09-01。

- [DEVELOPMENT.md](DEVELOPMENT.md)：本地环境、非 Docker 启动、配置、测试与常见问题。
- [API.md](API.md)：后端接口、请求示例、权限和错误处理。
- [FRONTEND_API.md](FRONTEND_API.md)：给前端开发者的稳定接口契约、字段模型和联调清单。
- [BUSINESS_SCENARIO_AND_ROADMAP.md](BUSINESS_SCENARIO_AND_ROADMAP.md)：当前业务定位、参与角色、告警闭环、部署场景和分阶段迭代方案。
- [PROJECT_STATUS.md](PROJECT_STATUS.md)：已实现、模拟实现和待接入能力的边界。
- [SMART_QA_OPTIMIZATION.md](SMART_QA_OPTIMIZATION.md)：智能问答架构、已完成优化、测试基线和后续路线。
- [DEPLOYMENT.md](DEPLOYMENT.md)：生产配置、容器化、上线检查和运维要求。
- [CLOUDFLARE_PAGES.md](CLOUDFLARE_PAGES.md)：Cloudflare Pages、`VITE_API_BASE`、本机后端隧道和断连排查。
- [schema.sql](schema.sql)：MySQL 初始建表脚本。
- [migrations/20260826_decimal_concentration.sql](migrations/20260826_decimal_concentration.sql)：已有数据库的浓度小数升级脚本。
- [migrations/20260826_extended_sensor_metrics.sql](migrations/20260826_extended_sensor_metrics.sql)：已有数据库的温度、湿度、电流、线缆温度、CO 值和蜂鸣器状态升级脚本。
- [migrations/20260828_role_workspace_3d_map.sql](migrations/20260828_role_workspace_3d_map.sql)：新增模拟楼栋、设备楼层/房间/坐标并为已有设备分配默认位置。
- [migrations/20260831_hazard_workflow.sql](migrations/20260831_hazard_workflow.sql)：新增隐患工单与流转记录表，支持上报、整改、复核和驳回闭环。
- [migrations/20260831_notification_audit.sql](migrations/20260831_notification_audit.sql)：新增通知一次性核查字段与索引。
- [migrations/20260901_vision_patrol.sql](migrations/20260901_vision_patrol.sql)：新增 AI 视觉疑似事件、钉钉结果和人工研判留痕表。
- [硬件说明](../hardware/README.md)：设备上报方式和 MQTT 主题约定。
- [视觉巡检说明](../ai-vision/README.md)：当前 DeepSeek/模拟规则闭环、历史 Flask 占位目录和真实摄像头接入边界。
- [前端说明](../smoke-detector-frontend/README.md)：当前页面结构、登录存储、小窗问答、3D 地图与构建方式。
- [RAG 服务说明](../rag-service/README.md)：领域过滤、本地知识回退与 Ollama 调用边界。

开发环境启动后还可访问动态 OpenAPI 文档：`http://127.0.0.1:8080/swagger-ui.html`。

接口发生变化时，应同时更新 `API.md`、`FRONTEND_API.md` 和相应前端类型定义；部署能力发生变化时，应同步更新 `PROJECT_STATUS.md`、`DEPLOYMENT.md` 与 `CLOUDFLARE_PAGES.md`。
