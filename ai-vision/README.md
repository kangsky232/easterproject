# 视觉复核服务占位说明

更新日期：2026-08-31。

`ai-vision` 当前只是 Flask 接口骨架，`POST /api/vision/verify` 无论请求内容都会返回 `data: null`，没有加载 YOLO 权重、读取摄像头或执行烟火识别。它没有健康检查、鉴权、输入校验或测试，也未加入根目录 `docker-compose.yml`，未接入当前 Spring Boot 告警流程，不应作为生产视觉能力展示。依赖文件中虽列有 OpenCV、Ultralytics 和 NumPy，当前代码并未调用它们。

仅验证占位服务时可运行：

```powershell
cd ai-vision
python -m pip install -r requirements.txt
python app.py
```

默认端口为 `5000`。正式实现至少需要补齐：

- 明确图片上传、对象存储地址或摄像头帧的输入协议。
- 固定并校验模型版本、置信度阈值和输出字段。
- 增加超时、文件大小、格式、鉴权和恶意文件防护。
- 保存可追溯的推理结果，并与人工复核结论区分。
- 提供健康检查、单元/集成测试和模型效果评估数据。

在这些工作完成前，平台中的“复核”只表示 Spring Boot 内置的规则辅助结论。
