# 智慧烟感监测系统

更新日期：2026-09-01。

面向社区烟感监测场景的全栈项目，包含 Vue 3 管理大屏、Spring Boot API、MySQL 数据存储、HTTP 设备接口、华为云 IoTDA MQTT 数据接入，以及可选的 RAG 和视觉复核扩展。

> 当前版本已实现 MQTT 入站遥测、六类传感器阈值告警、钉钉自动告警、机器人单聊广播、隐患上报—整改—复核闭环、DeepSeek 图片识别与模拟实时巡检、AI 疑似火情建档—钉钉推送—人工研判闭环、四角色差异化工作台、右下角白底智能问答小窗，以及可点击楼栋/楼层的模拟 3D 社区地图。真实短信、MQTT 设备广播和真实摄像头流仍属于外部集成项，详见 [功能状态](docs/PROJECT_STATUS.md)。

## 当前业务场景

系统当前用于住宅、老旧小区和出租屋的火灾风险监测演示：设备上报烟雾、温湿度、电流、线缆温度和 CO 数据，后端按预警/危险规则自动建警并发送钉钉单聊，物业或社区值班人员在 Web 端完成确认、复核、解决或误报归档。MQ-2 的 ppm 为近似估算，本系统当前适合演示和小范围试点验证，不能替代经过认证的消防报警设施。

居民、消防员、小区管理员和系统管理员登录后会获得不同的首页导航、功能页签与操作权限，后端仍是权限最终边界。所有角色均可上报隐患；居民只查看本人记录，消防员和管理员可接单整改，社区/系统管理员负责复核通过或驳回，全部动作写入流转时间线。通知审计会把投递失败且未核查的记录汇总为待办，消防员和管理员填写一次性核查结论后记录责任人和完成时间，前端可组合筛选、查看进度并导出 CSV。3D 地图支持直接点击楼栋立面和楼层，联动查看本层设备与状态；设备标记投影到当前视角最近立面，避免楼内深度造成跨层错觉，当前数据库中的 501 设备按 `floorNo=5` 显示在 5 层。当前 19 个楼层分别使用 19 张独立正常监控图，另有 2 张 AI 预警演示图。小区管理员和系统管理员可修改设备位置并同步写入 MySQL。演示图不是实时摄像头视频。

智能问答不再占用独立功能页；登录后通过右下角“AI 安全助手”打开约 `440×620 px` 的白底小窗，移动端按可视区域自适应。问答仍仅作安全处置辅助，不替代现场核验和 119 报警。

3D 社区页新增“AI 视觉实时巡检”：服务启动后默认暂停，消防员、小区管理员或系统管理员点击一次“开始巡检”后会立即识别第一帧，并持续按周期自动轮换 15 个模拟机位，直到点击“暂停巡检”；每轮全部出现一次且相邻不重复，暂停后不再自动分析或发送新的钉钉告警。其中 10 张正常、5 张疑似烟火。配置 `DEEPSEEK_API_KEY` 后由 `deepseek-v4-flash-vision-exp` 分析画面；没有密钥时明确显示并使用内置场景规则，绝不把规则结果冒充为模型结论。超过置信度阈值的疑似事件会写入 MySQL，并向已绑定员工发送钉钉单聊；有复核权限的工作人员必须填写依据，才能一次性确认为火情或排除误报。当前图片仍是静态演示素材，不是真实摄像头证据。

完整业务边界、告警口径和分阶段迭代计划见 [业务场景与迭代路线](docs/BUSINESS_SCENARIO_AND_ROADMAP.md)。

当前演示前端为 [https://easterproject.pages.dev](https://easterproject.pages.dev)，公网 API 固定为 [https://api.kangroom.eu.cc](https://api.kangroom.eu.cc)。API 通过 Cloudflare Named Tunnel 转发到本机 `127.0.0.1:8080`；重启服务无需更换域名，但本机后端、MySQL 和隧道进程必须保持运行。截至 2026-09-01 的本机配置中，数据库、MQTT 与钉钉单聊已配置，视觉巡检已启用但尚未提供 DeepSeek Key，因此运行在明确标注的 `SIMULATION_FALLBACK`，RAG 使用后端安全规则回退。这是运行配置说明，不是永久可用性承诺。

## 项目结构

```text
smart-smoke/
├── smoke-detector-frontend/  # Vue 3 + Vite + TypeScript + Pinia + ECharts
├── backend/                  # Spring Boot 3 + MyBatis-Plus + JWT
├── rag-service/              # 可选的知识检索与 Ollama 问答服务
├── ai-vision/                # 历史 Flask 视觉占位目录；当前视觉闭环实现在 backend
├── hardware/                 # 设备协议与硬件说明
├── deploy/                   # Nginx 等部署模板
├── docs/                     # API、开发、部署和功能状态文档
└── docker-compose.yml        # 可选的生产容器编排模板
```

## 本地快速启动（无需 Docker）

前置环境：JDK 17+、Maven 3.9+、Node.js 18+、MySQL 8。

1. 启动本机 MySQL，创建 `smart_smoke` 数据库并执行 `docs/schema.sql`。旧数据库按时间顺序执行 `docs/migrations/`；本次视觉巡检对应 `20260901_vision_patrol.sql`。后端启动也会兼容创建视觉事件表。
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

Windows PowerShell 若禁止执行 `npm.ps1`，请改用 `npm.cmd install` 和 `npm.cmd run dev`。

默认访问地址：

- 前端：`http://127.0.0.1:5173`（端口占用时 Vite 会选择下一个端口）
- 后端：`http://127.0.0.1:8080`
- Swagger：`http://127.0.0.1:8080/swagger-ui.html`
- 用户管理：系统管理员登录主前端后直接使用“用户管理”模块；独立备用页为 `http://127.0.0.1:8080/admin/index.html`

MQTT 和 RAG 服务不是本地核心功能启动的前置条件。配置 `MQTT_ENABLED=true` 后，后端会订阅华为云转发主题，接收烟雾浓度、温湿度、电流、线缆温度、CO 值和蜂鸣器状态，数值统一保留两位小数；RAG 不可用时后端会返回内置安全规则答案；未接短信供应商时 SMS 通知保留为待发送记录。

配置钉钉 Client ID/Client Secret 并启用 `DINGTALK_ENABLED` 后，后端通过 Stream 模式接收机器人私聊。员工首次私聊会自动绑定；网页广播以及烟雾、温湿度、电流、线缆温度和 CO 自动告警会发送到已启用且已绑定员工的钉钉单聊。可使用 `.\scripts\start-backend.ps1` 加载本机的 `.env.dingtalk.local` 并启动后端。

如需使用真实 DeepSeek 图片分析，在 Git 忽略的 `.env.vision.local` 中设置 `DEEPSEEK_API_KEY=<你的密钥>`，再运行 `.\scripts\start-backend.ps1`。脚本会同时加载 MQTT、钉钉和视觉本机配置；不要把真实密钥提交到 Git。没有 Key 时功能仍可完整演示，但界面、接口、事件和钉钉文案都会标注为模拟规则结果。

管理大屏默认以“实时”模式展示最近 120 条原始浓度数据，每 3 秒刷新一次，与当前华为云设备上报周期保持一致；24 小时、7 天和 30 天视图使用后端聚合趋势接口。MQTT 消息到达后端时会立即入库，不等待轮询。

## 开发账号说明

全新开发数据库会按 `application-dev.yml` 的引导配置创建管理员；已有数据库不会自动覆盖已有账号密码。团队联调时应由负责人分发开发账号，或通过 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 创建单独的本地管理员，不要在代码和文档中提交真实密码。

前端当前使用同源 `localStorage` 保存 JWT 和用户信息，因此同一浏览器配置文件下的多个标签页共享一个登录态，后登录的账号会覆盖先前 Token。需要同时联调两个账号时，使用普通窗口 + 无痕窗口，或不同浏览器/浏览器配置文件。

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
- [AI 视觉巡检说明](ai-vision/README.md)
- [部署与上线检查](docs/DEPLOYMENT.md)
- [Cloudflare Pages 与本机后端联调](docs/CLOUDFLARE_PAGES.md)
- [硬件与华为云 MQTT 接入](hardware/README.md)

`docker-compose.yml` 用于可选的服务器容器化部署，不是本地启动后端、前端或接入 MQTT 的强制要求。
