# 本地安全知识库问答服务

更新日期：2026-08-24。

该服务为烟感平台提供检索增强问答。它从内置安全知识库检索相关条目，再通过本机 Ollama 调用已安装的 `gpt-oss:120b-cloud` 生成回答；不使用 OpenAI API 密钥。

该标签由 Ollama 路由至云端推理服务，并非纯本地 GPU 推理。请先在运行服务的主机上确认 `ollama list` 能显示 `gpt-oss:120b-cloud`，且 Ollama 已完成登录或其他云端访问配置。Ollama 不可用或请求失败时，服务会自动返回原有的本地安全知识库答案。

明显与烟感、消防、设备状态和应急处置无关的问题会由服务端直接拒答，不调用模型；这样可以保持用途边界并避免不必要的云端请求。

问候语即使在前端选中了某条告警，也不会继承该告警的风险等级；只有安全领域问题或“这个怎么办”一类明确依赖当前告警的追问才会使用告警上下文。

相关问题的返回结果包含风险等级、摘要、立即措施、核验步骤、升级条件、安全提示和知识来源。由于 Ollama Cloud 当前不支持 API 的结构化输出参数，服务通过严格 JSON 提示和本地字段校验实现相同的稳定响应；校验失败时自动回退到本地知识库。

风险等级还会与知识库和当前告警计算出的基线比较，模型只能提高等级，不能把已知风险降级。

```powershell
cd rag-service
python -m pip install -r requirements.txt
python app.py
```

服务运行在 `http://127.0.0.1:5001`，健康检查地址为 `/api/health`，问答接口为 `POST /api/chat/query`。

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
| `RAG_TIMEOUT_SECONDS` | `130` | 后端等待 RAG 服务的时间，应大于模型超时。 |

该服务不能替代消防部门的正式处置要求。修改后可使用 `python -m unittest discover -v` 和 `python -m py_compile app.py` 做基础检查。
