import json
import unittest
from unittest.mock import patch
import urllib.error

import app


class FakeOllamaResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        return json.dumps(self.payload).encode("utf-8")


class OllamaAnswerTest(unittest.TestCase):
    @patch.object(app, "OLLAMA_BASE_URL", "http://127.0.0.1:11434")
    @patch.object(app, "OLLAMA_MODEL", "gpt-oss:120b-cloud")
    @patch("app.urllib.request.urlopen")
    def test_uses_local_ollama_chat_endpoint(self, urlopen):
        model_answer = {
            "riskLevel": "MEDIUM",
            "summary": "设备离线需要尽快核查供电与通信。",
            "immediateActions": ["确认设备供电是否正常"],
            "verificationSteps": ["检查最后一次心跳时间"],
            "escalationConditions": ["持续离线时安排现场检修"],
            "safetyNotice": "设备恢复前应采用人工巡检作为补充。",
        }
        urlopen.return_value = FakeOllamaResponse({"message": {"content": json.dumps(model_answer, ensure_ascii=False)}})

        answer = app.answer_question("设备离线后怎么办？", {"alertType": "OFFLINE", "deviceId": "SMOKE-001"})

        self.assertEqual(answer["summary"], "设备离线需要尽快核查供电与通信。")
        self.assertEqual(answer["riskLevel"], "MEDIUM")
        self.assertIn("立即措施", answer["answer"])
        self.assertEqual(answer["source"], "OLLAMA")
        self.assertEqual(answer["model"], "gpt-oss:120b-cloud")
        request_to_ollama = urlopen.call_args.args[0]
        self.assertEqual(request_to_ollama.full_url, "http://127.0.0.1:11434/api/chat")
        request_body = json.loads(request_to_ollama.data.decode("utf-8"))
        self.assertEqual(request_body["model"], "gpt-oss:120b-cloud")
        self.assertFalse(request_body["stream"])

    @patch("app.urllib.request.urlopen", side_effect=urllib.error.URLError("unavailable"))
    def test_falls_back_to_knowledge_base_when_ollama_is_unavailable(self, urlopen):
        answer = app.answer_question("设备离线后怎么办？", {"alertType": "OFFLINE"})

        self.assertEqual(answer["source"], "LOCAL_SAFETY_KNOWLEDGE_BASE")
        self.assertIn("供电", answer["answer"])
        self.assertEqual(answer["riskLevel"], "MEDIUM")

    @patch("app.urllib.request.urlopen")
    def test_declines_unrelated_question_without_calling_ollama(self, urlopen):
        answer = app.answer_question("帮我写一首关于夏天的诗", None)

        self.assertEqual(answer["source"], "LOCAL_SAFETY_SCOPE_GUARD")
        self.assertIn("仅支持", answer["answer"])
        self.assertEqual(answer["riskLevel"], "UNKNOWN")
        urlopen.assert_not_called()

    @patch("app.urllib.request.urlopen")
    def test_greeting_does_not_inherit_selected_alert_risk(self, urlopen):
        answer = app.answer_question("你好", {"alertType": "OFFLINE", "deviceId": "SMOKE-001"})

        self.assertEqual(answer["source"], "LOCAL_GREETING")
        self.assertEqual(answer["riskLevel"], "UNKNOWN")
        self.assertIn("你好", answer["answer"])
        urlopen.assert_not_called()

    @patch("app.urllib.request.urlopen")
    def test_unrelated_question_does_not_inherit_selected_alert_risk(self, urlopen):
        answer = app.answer_question("帮我写一首诗", {"alertType": "OFFLINE"})

        self.assertEqual(answer["source"], "LOCAL_SAFETY_SCOPE_GUARD")
        self.assertEqual(answer["riskLevel"], "UNKNOWN")
        urlopen.assert_not_called()

    def test_vague_follow_up_can_use_selected_alert(self):
        self.assertTrue(app.is_safety_question("这个怎么办？", {"alertType": "OFFLINE"}))

    @patch("app.urllib.request.urlopen")
    def test_falls_back_when_model_returns_invalid_structure(self, urlopen):
        urlopen.return_value = FakeOllamaResponse({"message": {"content": "这不是 JSON"}})

        answer = app.answer_question("烟雾告警怎么处理？", {"alertType": "SMOKE"})

        self.assertEqual(answer["source"], "LOCAL_SAFETY_KNOWLEDGE_BASE")
        self.assertEqual(answer["riskLevel"], "HIGH")

    def test_model_cannot_lower_knowledge_base_risk(self):
        model_answer = {
            "riskLevel": "UNKNOWN",
            "summary": "需要检查设备。",
            "immediateActions": [],
            "verificationSteps": [],
            "escalationConditions": [],
            "safetyNotice": "注意安全。",
        }
        knowledge = app.retrieve_knowledge("设备离线后怎么办？", None)

        answer = app.enforce_risk_floor(model_answer, knowledge, None)

        self.assertEqual(answer["riskLevel"], "MEDIUM")


if __name__ == "__main__":
    unittest.main()
