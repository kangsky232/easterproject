# 智慧烟感预警系统前端

与 `backend/` 中 Spring Boot 服务配套的管理大屏，基于 **Vue 3 + Vite + TypeScript + Pinia + ECharts**。

## 技术栈

- Vue 3（Composition API + `<script setup>`）
- Vite 5
- TypeScript
- Pinia（状态管理）
- ECharts（浓度趋势图）

## 目录结构

```
smoke-detector-frontend/
├── index.html             # Vite 入口
├── vite.config.ts         # 构建 / 开发代理（/api → http://127.0.0.1:8080）
├── package.json
├── tsconfig.json
├── nginx.conf             # 生产环境 nginx 反向代理
├── Dockerfile             # 多阶段构建：node 构建 → nginx 托管
├── start.bat              # Windows 一键启动开发服务器
└── src/
    ├── main.ts            # 应用入口
    ├── App.vue            # 顶层布局与标签导航
    ├── style.css          # 全局大屏样式（暗色主题）
    ├── constants.ts       # 状态 / 类型 / 颜色映射
    ├── api/               # 请求封装、类型、字段映射与接口定义
    ├── store/             # Pinia store（认证、数据、轮询、告警通知）
    ├── composables/       # useClock、useSpeech
    ├── utils/             # 格式化与音频提示
    └── components/        # 视图、弹窗、覆盖层组件
```

## 启动

1. 启动 MySQL，并按 `docs/schema.sql` 初始化 `smart_smoke` 数据库。
2. 在 `backend/` 目录启动服务；默认地址为 `http://127.0.0.1:8080`。
3. 在本目录执行：

```bash
npm install
npm run dev
```

4. 浏览器打开 `http://127.0.0.1:5173`，使用后端账户登录。

开发环境由 Vite 将 `/api` 代理到 `http://127.0.0.1:8080`，因此无需额外处理跨域。首次启动后端会创建默认系统管理员：`admin / 000000`。部署时请通过环境变量修改该密码和 JWT 密钥。

## 构建与部署

```bash
npm run build          # 输出到 dist/（含 vue-tsc 类型检查）
npm run preview        # 本地预览构建产物
```

生产环境使用 Docker 多阶段构建（见 `Dockerfile`），nginx 将 `/api` 反向代理到后端，其余请求回退到 `index.html`（SPA 路由）。也可通过根目录 `docker-compose.yml` 一键部署。

## 已对接功能

- JWT 登录和统一 `{ code, message, data }` 响应处理
- 设备总览、设备列表和最近 120 条历史浓度趋势
- 告警查询、确认与处置
- 设备绑定、编辑、阈值设置与解绑
- 向当前选中的设备创建广播指令
- 告警复核、误报标记，以及由新告警触发的 APP / 短信模拟通知记录
- 基于告警上下文的安全知识问答（优先使用本地 RAG 服务，服务不可用时安全降级）
- 每 15 秒轮询后端数据；后端当前未提供 WebSocket 推送接口
- “模拟告警”通过 `POST /api/telemetry` 上报一次超阈值数据

摄像头流、真实短信/APP 通道、LLM 和 MQTT Broker 仍是可替换的外部适配点；当前项目内置的是可本地验收的复核规则、通知模拟和安全知识问答。
