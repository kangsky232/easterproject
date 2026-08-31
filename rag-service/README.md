# 本地安全知识库问答服务

更新日期：2026-08-31。

该服务为烟感平台提供轻量检索增强问答。当前内置 6 条安全知识，使用加权关键词做 Top-1 匹配，再尝试通过本机 Ollama 调用 `gpt-oss:120b-cloud` 生成结构化回答；不使用 OpenAI API 密钥。Top-K、向量库和持久化对话历史尚未实现。

该标签由 Ollama 路由至云端推理服务，并非纯本地 GPU 推理。请先在运行服务的主机上确认 `ollama list` 能显示 `gpt-oss:120b-cloud`，且 Ollama 已完成登录或其他云端访问配置。Ollama 不可用或请求失败时，服务会自动返回原有的本地安全知识库答案。

明显与烟感、消防、设备状态和应急处置无关的问题会由服务端直接拒答，不调用模型；这样可以保持用途边界并避免不必要的云端请求。

问候语即使在前端选中了某条告警，也不会继承该告警的风险等级；安全领域问题或“这个怎么办”一类明确追问可以使用告警上下文。不过当前 Spring Boot 传入的是中文告警类型标签，而本服务的类型增强分支只识别英文 `smoke` / `offline`；直接关键词问题仍可检索，模糊离线追问可能误判上下文，跨服务契约仍需修复。

相关问题的返回结果包含风险等级、摘要、立即措施、核验步骤、升级条件、安全提示和知识来源。由于 Ollama Cloud 当前不支持 API 的结构化输出参数，服务通过严格 JSON 提示和本地字段校验实现相同的稳定响应；校验失败时自动回退到本地知识库。

风险等级还会与知识库和当前告警计算出的基线比较，模型只能提高等级，不能把已知风险降级。

```powershell
cd rag-service
python -m pip install -r requirements.txt
python app.py
```

服务运行在 `http://127.0.0.1:5001`，健康检查地址为 `/api/health`，问答接口为 `POST /api/chat/query`。

`/api/health` 只确认 Flask 服务与内置知识库可响应，并返回知识条目数、提供方标签和模型名；它不会向 Ollama 发起推理探测。因此健康检查为 `UP` 不等于模型一定可用，实际回答应同时查看返回的 `source`。2026-08-31 主系统联调快照为 `knowledgeBase=FALLBACK_ONLY`，表示当时 Spring Boot 未连通本服务，问答走后端内置规则。

请求示例：

```json
{
  "question": "设备离线后怎么处理？",
  "alert": {
    "deviceId": "SMOKE-001",
    "alertType": "OFFLINE"
  }
}
```

后端通过 `RAG_SERVICE_URL` 配置服务地址；服务不可用时，后端会回退到内置的安全问答规则，避免页面无响应。

可选配置：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | 本机 Ollama 地址；Docker 默认使用 `http://host.docker.internal:11434`。 |
| `OLLAMA_MODEL` | `gpt-oss:120b-cloud` | Ollama 已安装的云端模型标签。 |
| `OLLAMA_TIMEOUT_SECONDS` | `120` | 单次模型推理最长等待时间。 |
| `OLLAMA_MAX_TOKENS` | `768` | 单次结构化回答的最大生成 token 数。 |
| `OLLAMA_KEEP_ALIVE` | `10m` | 传给 Ollama 的模型保活设置。 |
| `RAG_TIMEOUT_SECONDS` | `130` | 后端等待 RAG 服务的时间，应大于模型超时。 |

请求中的 `question` 必填且最长 500 个字符，`alert` 如存在必须是对象。服务当前由 `python app.py` 和 Dockerfile 启动 Flask 开发服务器；正式环境应改用 Gunicorn 等 WSGI 服务，并补充并发、超时、优雅退出、限流、熔断和可观测性。

该服务不能替代消防部门的正式处置要求。修改后可使用 `python -m unittest discover -v` 和 `python -m py_compile app.py` 做基础检查。
