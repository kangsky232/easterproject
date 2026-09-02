# 项目发布包运行说明

发布版本：`0.1.0`  
生成日期：2026-09-02

## 包内结构

```text
smart-smoke-release-0.1.0/
├── backend/
│   └── smart-smoke-backend-0.1.0.jar
├── frontend/
│   └── dist/
├── database/
│   ├── schema.sql
│   └── migrations/
├── config/
│   └── .env.example
├── docs/
│   ├── DEPLOYMENT.md
│   └── RELEASE_PACKAGE.md
└── README.md
```

这是可运行发布包，不包含 Java、Maven、Node.js、npm、MySQL、Nginx 等第三方软件安装包，也不包含源码依赖缓存或真实密钥。

## 运行条件

- JRE/JDK 17 或更高版本
- MySQL 8
- Nginx 或其他能够托管 Vue 静态文件并反向代理 `/api` 的 Web 服务器
- DeepSeek、钉钉和 MQTT 凭据按需提供

运行发布包不需要 Maven、Node.js 或 npm；这些依赖已经在构建机器上用于生成 JAR 和 `dist`。

## 1. 初始化数据库

创建 `smart_smoke` 数据库，然后执行：

```text
database/schema.sql
```

旧数据库按文件名时间顺序执行 `database/migrations/` 中的脚本。

## 2. 启动后端 JAR

发布包不会自动读取 `.env` 文件。运行前应通过操作系统、进程管理器、容器平台或机密管理服务注入环境变量。

Windows PowerShell 演示示例：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/smart_smoke?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = ''
$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:8088'
java -jar .\backend\smart-smoke-backend-0.1.0.jar
```

正式环境还必须设置强 `JWT_SECRET`，并使用非 `root`、非空密码的专用数据库账户。DeepSeek、钉钉和 MQTT 变量参考 `config/.env.example`，不要把真实值写回发布包。

验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

## 3. 托管前端

`frontend/dist` 已按同源 `/api` 构建，不能直接双击 `index.html`。应使用 Nginx 或其他 Web 服务器托管，并把 `/api` 反向代理到 `http://127.0.0.1:8080`。

最小 Nginx 逻辑如下，部署时把静态目录替换为实际绝对路径：

```nginx
server {
    listen 8088;
    server_name _;
    root /absolute/path/to/frontend/dist;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启动后访问 `http://127.0.0.1:8088`。

如果前端和后端使用不同域名，需要从源码使用正确的 `VITE_API_BASE` 重新构建前端，不能在已经生成的 `dist` 中修改普通配置文件完成切换。

## 4. 验收

- 后端 `/api/health` 返回 `status=UP`、`database=UP`。
- 前端能登录并显示后端在线。
- `/api/system/capabilities` 的外部服务状态符合实际配置。
- DeepSeek 巡检启动后有成功分析结果。
- 钉钉接收人完成机器人私聊绑定后，能够收到新告警或广播。

完整的 Cloudflare、Docker、排错、安全和备份说明见 `docs/DEPLOYMENT.md`。
