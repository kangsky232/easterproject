from __future__ import annotations

from dataclasses import dataclass
import json
import os
from typing import Any
import urllib.error
import urllib.request

from flask import Flask, jsonify, request

app = Flask(__name__)

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gpt-oss:120b-cloud")
OLLAMA_KEEP_ALIVE = os.getenv("OLLAMA_KEEP_ALIVE", "10m")


def positive_integer_setting(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
        return value if value > 0 else default
    except ValueError:
        return default


OLLAMA_TIMEOUT_SECONDS = positive_integer_setting("OLLAMA_TIMEOUT_SECONDS", 120)
OLLAMA_MAX_TOKENS = positive_integer_setting("OLLAMA_MAX_TOKENS", 768)

SYSTEM_PROMPT = """你是烟感平台的安全问答助手。请用中文、简洁且可执行地回答。
只能依据给出的安全知识和告警上下文回答；知识不足时明确说明，并建议现场核验或联系消防救援部门。
对于火情、持续烟雾或人身风险，优先提示立即疏散、拨打 119 和遵循现场应急预案。
不要编造设备状态、检测结果、法规要求或处置结论，也不要建议未经现场核验就关闭告警。
严格输出提示中要求的单个 JSON 对象，不要使用 Markdown 代码块，也不要展示推理过程。"""

SAFETY_DOMAIN_KEYWORDS = (
    "烟感", "烟雾", "火灾", "火情", "着火", "消防", "119", "告警", "报警", "疏散", "逃生",
    "应急", "设备", "传感器", "离线", "心跳", "网络", "通信", "供电", "电源", "电池",
    "阈值", "浓度", "ppm", "误报", "广播", "检修",
)

GREETING_PHRASES = {
    "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗", "你是谁", "谢谢", "感谢", "再见",
}

ALERT_FOLLOW_UP_KEYWORDS = (
    "怎么办", "怎么处理", "如何处理", "怎么做", "下一步", "严重吗", "危险吗", "需要做什么", "帮我处理",
)

RISK_LABELS = {
    "UNKNOWN": "未评估",
    "LOW": "低",
    "MEDIUM": "中",
    "HIGH": "高",
    "CRITICAL": "紧急",
}
RISK_PRIORITY = {risk_level: priority for priority, risk_level in enumerate(RISK_LABELS)}

SPECIFIC_KEYWORDS = {
    "alarm-process": ("报警", "告警处置", "处置流程"),
    "evacuation": ("疏散", "逃生", "着火", "火灾", "119", "电梯"),
    "offline-device": ("离线", "心跳", "掉线", "电池"),
    "threshold": ("阈值", "ppm", "灵敏度"),
    "false-alarm": ("误报", "不是火灾"),
    "broadcast": ("广播", "喊话", "下发"),
}


@dataclass(frozen=True)
class KnowledgeArticle:
    identifier: str
    title: str
    keywords: tuple[str, ...]
    answer: str


KNOWLEDGE_BASE = (
    KnowledgeArticle(
        "alarm-process",
        "告警确认与处置流程",
        ("告警", "报警", "确认", "处置", "处理", "流程", "怎么办"),
        "收到烟雾告警后，应先查看设备、浓度和时间；随后安排人员现场核验。确认存在风险时立即疏散并联系消防救援；排除风险后可标记为误报。处置完成后应记录结果。",
    ),
    KnowledgeArticle(
        "evacuation",
        "火情疏散指引",
        ("疏散", "逃生", "火情", "着火", "火灾", "119", "电梯"),
        "发现火情或高风险烟雾时，应立即通知人员沿安全通道有序疏散，不乘坐电梯，帮助行动不便人员，并拨打 119。不要返回危险区域取物品。",
    ),
    KnowledgeArticle(
        "offline-device",
        "设备离线处理",
        ("离线", "心跳", "掉线", "网络", "供电", "电池"),
        "设备离线时，请依次检查电源、电池、网络连接和心跳上报。设备恢复上报后，系统会自动关闭对应离线告警；若持续离线，应安排现场检修。",
    ),
    KnowledgeArticle(
        "threshold",
        "烟雾阈值设置",
        ("阈值", "浓度", "ppm", "设置", "调整", "灵敏度"),
        "阈值应结合安装位置、通风条件和现场基线浓度设置。修改后应进行受控测试，避免阈值过低造成频繁误报，也不能因降低告警频率而设置得过高。",
    ),
    KnowledgeArticle(
        "false-alarm",
        "误报标记原则",
        ("误报", "排除", "复核", "标记", "不是火灾"),
        "只有在完成现场核验、确认不存在火情或持续烟雾风险后，才可标记为误报。若证据不足，应保持告警并升级处置，而不是直接关闭。",
    ),
    KnowledgeArticle(
        "broadcast",
        "联动广播使用",
        ("广播", "通知", "喊话", "下发"),
        "联动广播应使用简短、明确的指令，例如提示所在区域人员按指定安全通道疏散。创建指令后请关注下发状态，并在必要时采用人工广播作为备份。",
    ),
)


def normalize(value: Any) -> str:
    return str(value or "").strip().lower()


def score(article: KnowledgeArticle, question: str, alert: dict[str, Any] | None) -> int:
    points = sum(3 for keyword in article.keywords if keyword in question)
    if any(keyword in question for keyword in SPECIFIC_KEYWORDS.get(article.identifier, ())):
        points += 2
    if alert:
        alert_type = normalize(alert.get("alertType"))
        if alert_type == "smoke" and article.identifier in {"alarm-process", "evacuation", "threshold", "false-alarm"}:
            points += 1
        if alert_type == "offline" and article.identifier == "offline-device":
            points += 2
    return points


def alert_context(alert: dict[str, Any] | None) -> str:
    if not alert:
        return ""
    device_id = str(alert.get("deviceId") or "当前设备")
    alert_type = normalize(alert.get("alertType"))
    if alert_type == "offline":
        return f"\n\n当前上下文：设备 {device_id} 处于离线告警状态，请优先核查供电和通信。"
    concentration = alert.get("concentration")
    threshold = alert.get("threshold")
    if concentration is not None and threshold is not None:
        return f"\n\n当前上下文：设备 {device_id} 的烟雾浓度为 {concentration} ppm，阈值为 {threshold} ppm。请结合现场情况处理。"
    return f"\n\n当前上下文：设备 {device_id} 存在烟雾告警，请先进行现场核验。"


def normalized_phrase(question: str) -> str:
    return "".join(character for character in question.lower().strip() if character.isalnum() or "\u4e00" <= character <= "\u9fff")


def is_greeting(question: str) -> bool:
    return normalized_phrase(question) in GREETING_PHRASES


def is_safety_question(question: str, alert: dict[str, Any] | None) -> bool:
    if any(keyword in question for keyword in SAFETY_DOMAIN_KEYWORDS):
        return True
    return alert is not None and any(keyword in question for keyword in ALERT_FOLLOW_UP_KEYWORDS)


def greeting_answer() -> dict[str, Any]:
    answer = "你好！我可以协助处理烟感告警、人员疏散、设备离线、阈值设置和误报复核等问题。"
    return {
        "answer": answer,
        "source": "LOCAL_GREETING",
        "sources": [],
        "confidence": "HIGH",
        "riskLevel": "UNKNOWN",
        "summary": answer,
        "immediateActions": [],
        "verificationSteps": [],
        "escalationConditions": [],
        "safetyNotice": "",
    }


def out_of_scope_answer() -> dict[str, Any]:
    summary = "本助手仅支持烟感告警、消防安全、设备状态和应急处置相关问题。请描述具体的告警或设备情况，我会协助处理。"
    return {
        "answer": summary,
        "source": "LOCAL_SAFETY_SCOPE_GUARD",
        "sources": [],
        "confidence": "HIGH",
        "riskLevel": "UNKNOWN",
        "summary": summary,
        "immediateActions": [],
        "verificationSteps": [],
        "escalationConditions": [],
        "safetyNotice": "如存在实际火情或人身风险，请立即疏散并拨打 119。",
    }


def retrieve_knowledge(question: str, alert: dict[str, Any] | None) -> dict[str, Any]:
    ranked = sorted(
        ((score(article, question, alert), article) for article in KNOWLEDGE_BASE),
        key=lambda item: item[0],
        reverse=True,
    )
    best_score, best = ranked[0]
    if best_score == 0:
        answer = "我暂未找到与问题直接匹配的操作指引。你可以询问告警处置、人员疏散、设备离线、阈值、误报或联动广播。" + alert_context(alert)
        return {
            "answer": answer,
            "source": "LOCAL_SAFETY_KNOWLEDGE_BASE",
            "sources": [],
            "confidence": "LOW",
        }
    confidence = "HIGH" if best_score >= 3 else "MEDIUM"
    return {
        "answer": best.answer + alert_context(alert),
        "source": "LOCAL_SAFETY_KNOWLEDGE_BASE",
        "sources": [{"id": best.identifier, "title": best.title}],
        "confidence": confidence,
    }


def parse_string_list(value: Any, limit: int = 5) -> list[str] | None:
    if not isinstance(value, list):
        return None
    items = [str(item).strip() for item in value if isinstance(item, str) and item.strip()]
    return items[:limit]


def parse_structured_answer(content: str) -> dict[str, Any] | None:
    text = content.strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1] if "\n" in text else text
        text = text.rsplit("```", 1)[0].strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        payload = json.loads(text[start : end + 1])
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    risk_level = str(payload.get("riskLevel") or "").strip().upper()
    summary = str(payload.get("summary") or "").strip()
    safety_notice = str(payload.get("safetyNotice") or "").strip()
    immediate_actions = parse_string_list(payload.get("immediateActions"))
    verification_steps = parse_string_list(payload.get("verificationSteps"))
    escalation_conditions = parse_string_list(payload.get("escalationConditions"), 4)
    if (
        risk_level not in RISK_LABELS
        or not summary
        or not safety_notice
        or immediate_actions is None
        or verification_steps is None
        or escalation_conditions is None
    ):
        return None
    return {
        "riskLevel": risk_level,
        "summary": summary,
        "immediateActions": immediate_actions,
        "verificationSteps": verification_steps,
        "escalationConditions": escalation_conditions,
        "safetyNotice": safety_notice,
    }


def format_structured_answer(answer: dict[str, Any]) -> str:
    lines = [f"风险等级：{RISK_LABELS[answer['riskLevel']]}", answer["summary"]]
    sections = (
        ("立即措施", answer["immediateActions"]),
        ("核验步骤", answer["verificationSteps"]),
        ("升级条件", answer["escalationConditions"]),
    )
    for title, items in sections:
        if items:
            lines.extend(["", f"{title}："])
            lines.extend(f"{index}. {item}" for index, item in enumerate(items, 1))
    lines.extend(["", f"安全提示：{answer['safetyNotice']}"])
    return "\n".join(lines)


def fallback_risk_level(knowledge: dict[str, Any], alert: dict[str, Any] | None) -> str:
    alert_type = normalize((alert or {}).get("alertType"))
    if alert_type == "smoke":
        return "HIGH"
    if alert_type == "offline":
        return "MEDIUM"
    source_ids = {str(source.get("id") or "") for source in knowledge.get("sources") or []}
    if source_ids & {"alarm-process", "evacuation"}:
        return "HIGH"
    if source_ids & {"offline-device", "false-alarm", "broadcast"}:
        return "MEDIUM"
    if "threshold" in source_ids:
        return "LOW"
    return "UNKNOWN"


def add_fallback_structure(knowledge: dict[str, Any], alert: dict[str, Any] | None) -> dict[str, Any]:
    return {
        **knowledge,
        "riskLevel": fallback_risk_level(knowledge, alert),
        "summary": knowledge["answer"],
        "immediateActions": [],
        "verificationSteps": [],
        "escalationConditions": [],
        "safetyNotice": "模型暂不可用，以上内容来自本地安全知识库；如存在实际火情或人身风险，请立即疏散并拨打 119。",
    }


def enforce_risk_floor(answer: dict[str, Any], knowledge: dict[str, Any], alert: dict[str, Any] | None) -> dict[str, Any]:
    baseline = fallback_risk_level(knowledge, alert)
    if RISK_PRIORITY[answer["riskLevel"]] < RISK_PRIORITY[baseline]:
        return {**answer, "riskLevel": baseline}
    return answer


def ask_ollama(question: str, knowledge: dict[str, Any]) -> dict[str, Any] | None:
    sources = knowledge.get("sources") or []
    source_titles = "、".join(str(source.get("title") or "") for source in sources) or "无直接匹配条目"
    user_prompt = (
        f"安全知识：\n{knowledge['answer']}\n\n"
        f"匹配条目：{source_titles}\n\n"
        f"用户问题：{question}\n\n"
        "请结合以上资料回答；资料与问题不匹配时，请明确说明限制。"
        "只输出一个合法 JSON 对象，字段必须完整，格式如下：\n"
        '{"riskLevel":"UNKNOWN|LOW|MEDIUM|HIGH|CRITICAL",'
        '"summary":"一句话结论",'
        '"immediateActions":["立即执行的措施，最多5项"],'
        '"verificationSteps":["现场核验步骤，最多5项"],'
        '"escalationConditions":["需要升级处置的条件，最多4项"],'
        '"safetyNotice":"必要的安全边界提示"}'
    )
    body = json.dumps(
        {
            "model": OLLAMA_MODEL,
            "stream": False,
            "keep_alive": OLLAMA_KEEP_ALIVE,
            "options": {"temperature": 0, "num_predict": OLLAMA_MAX_TOKENS},
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
        },
        ensure_ascii=False,
    ).encode("utf-8")
    request_to_ollama = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/chat",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request_to_ollama, timeout=OLLAMA_TIMEOUT_SECONDS) as response:
            payload = json.loads(response.read().decode("utf-8"))
        answer = payload.get("message", {}).get("content", "")
        return parse_structured_answer(answer) if isinstance(answer, str) else None
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError):
        return None


def answer_question(question: str, alert: dict[str, Any] | None) -> dict[str, Any]:
    if is_greeting(question):
        return greeting_answer()
    if not is_safety_question(question, alert):
        return out_of_scope_answer()
    knowledge = retrieve_knowledge(question, alert)
    model_answer = ask_ollama(question, knowledge)
    if model_answer:
        model_answer = enforce_risk_floor(model_answer, knowledge, alert)
        return {
            "answer": format_structured_answer(model_answer),
            "source": "OLLAMA",
            "model": OLLAMA_MODEL,
            "sources": knowledge["sources"],
            "confidence": knowledge["confidence"],
            **model_answer,
        }
    return add_fallback_structure(knowledge, alert)


@app.get("/api/health")
def health():
    return jsonify(
        {
            "code": 0,
            "message": "success",
            "data": {
                "status": "UP",
                "knowledgeArticles": len(KNOWLEDGE_BASE),
                "llmProvider": "OLLAMA",
                "llmModel": OLLAMA_MODEL,
            },
        }
    )


@app.post("/api/chat/query")
def query():
    payload = request.get_json(silent=True) or {}
    question = normalize(payload.get("question"))
    if not question:
        return jsonify({"code": 400, "message": "question 不能为空", "data": None}), 400
    if len(question) > 500:
        return jsonify({"code": 400, "message": "question 长度不能超过 500", "data": None}), 400
    alert = payload.get("alert")
    if alert is not None and not isinstance(alert, dict):
        return jsonify({"code": 400, "message": "alert 必须是对象", "data": None}), 400
    return jsonify({"code": 0, "message": "success", "data": answer_question(question, alert)})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001)
