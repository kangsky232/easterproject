<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import { useSpeech } from '@/composables/useSpeech'
import AppIcon from '@/components/AppIcon.vue'
import type { ChatResponse } from '@/api/types'

const store = useDashboardStore()

interface ChatMessage {
  role: 'user' | 'bot'
  text: string
  response?: ChatResponse
}

const CHAT_QUICK = ['如何确认和处置告警？', '设备离线如何处理？', '如何设置烟雾阈值？']

const messages = ref<ChatMessage[]>([])
const input = ref('')
const logEl = ref<HTMLDivElement | null>(null)

function scrollToBottom(): void {
  void nextTick(() => {
    if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
  })
}

function addMessage(role: ChatMessage['role'], text: string, response?: ChatResponse): void {
  messages.value.push({ role, text, response })
  scrollToBottom()
}

const RISK_LABELS: Record<string, string> = {
  UNKNOWN: '未评估',
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '紧急',
}

function hasStructuredDetails(response?: ChatResponse): boolean {
  if (!response) return false
  return response.riskLevel !== undefined && (
    response.riskLevel !== 'UNKNOWN'
    || Boolean(response.immediateActions?.length)
    || Boolean(response.verificationSteps?.length)
    || Boolean(response.escalationConditions?.length)
  )
}

function riskLabel(riskLevel?: string): string {
  return RISK_LABELS[riskLevel || 'UNKNOWN'] || '未评估'
}

async function send(text?: string): Promise<void> {
  const question = (text ?? input.value).trim()
  if (!question) return
  input.value = ''
  addMessage('user', question)
  const response = await store.sendChat(question)
  if (response) addMessage('bot', response.answer || '暂时无法回答该问题。', response)
}

function onVoiceResult(text: string): void {
  input.value = text
  void send(text)
}

const { listening, toggle } = useSpeech(onVoiceResult, (message) => addMessage('bot', message))

addMessage('bot', '你好，我是智能问答助手。可以问我告警处置、疏散、设备离线和阈值设置等问题。')
</script>

<template>
  <div class="chat-wrap">
    <div ref="logEl" class="chat-log">
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="msg"
        :class="[message.role === 'user' ? 'msg-user' : 'msg-bot', { 'msg-safety': hasStructuredDetails(message.response) }]"
      >
        <template v-if="message.response && hasStructuredDetails(message.response)">
          <div class="safety-answer-header">
            <strong>安全处置建议</strong>
            <span class="risk-badge" :class="`risk-${(message.response.riskLevel || 'UNKNOWN').toLowerCase()}`">
              {{ riskLabel(message.response.riskLevel) }}
            </span>
          </div>
          <p class="safety-summary">{{ message.response.summary || message.text }}</p>
          <section v-if="message.response.immediateActions?.length" class="safety-section">
            <h4>立即措施</h4>
            <ol><li v-for="item in message.response.immediateActions" :key="item">{{ item }}</li></ol>
          </section>
          <section v-if="message.response.verificationSteps?.length" class="safety-section">
            <h4>核验步骤</h4>
            <ol><li v-for="item in message.response.verificationSteps" :key="item">{{ item }}</li></ol>
          </section>
          <section v-if="message.response.escalationConditions?.length" class="safety-section">
            <h4>升级条件</h4>
            <ul><li v-for="item in message.response.escalationConditions" :key="item">{{ item }}</li></ul>
          </section>
          <p v-if="message.response.safetyNotice" class="safety-notice">{{ message.response.safetyNotice }}</p>
          <p v-if="message.response.sources?.length" class="safety-sources">
            依据：{{ message.response.sources.map((source) => source.title).join('、') }}
          </p>
        </template>
        <template v-else>{{ message.text }}</template>
      </div>
    </div>
    <div class="chat-quick">
      <button v-for="question in CHAT_QUICK" :key="question" type="button" @click="send(question)">
        {{ question }}
      </button>
    </div>
    <form class="chat-input" @submit.prevent="send()">
      <input
        v-model="input"
        type="text"
        placeholder="问我：报警流程 / 怎么疏散 / 阈值怎么设…"
        autocomplete="off"
      />
      <button type="button" class="btn-primary btn-mic" :class="{ listening }" title="语音输入" @click="toggle()">
        <AppIcon name="mic" :size="16" />
      </button>
      <button type="submit" class="btn-primary">发送</button>
    </form>
  </div>
</template>
