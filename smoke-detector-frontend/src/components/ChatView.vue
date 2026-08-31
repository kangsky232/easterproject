<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
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
const inputEl = ref<HTMLInputElement | null>(null)
const open = ref(false)
const sending = ref(false)

function scrollToBottom(): void {
  void nextTick(() => {
    if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
  })
}

function focusInput(): void {
  void nextTick(() => inputEl.value?.focus())
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

function openChat(): void {
  open.value = true
}

function closeChat(): void {
  open.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && open.value) closeChat()
}

async function send(text?: string): Promise<void> {
  const question = (text ?? input.value).trim()
  if (!question || sending.value) return
  input.value = ''
  addMessage('user', question)
  sending.value = true
  try {
    const response = await store.sendChat(question)
    if (response) addMessage('bot', response.answer || '暂时无法回答该问题。', response)
  } finally {
    sending.value = false
    focusInput()
  }
}

function onVoiceResult(text: string): void {
  input.value = text
  void send(text)
}

const { listening, toggle } = useSpeech(onVoiceResult, (message) => addMessage('bot', message))

watch(open, (visible) => {
  document.body.classList.toggle('chat-assistant-open', visible)
  if (visible) {
    scrollToBottom()
    focusInput()
  }
})

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.classList.remove('chat-assistant-open')
})

addMessage('bot', '你好，我是智能问答助手。可以问我告警处置、疏散、设备离线和阈值设置等问题。')
</script>

<template>
  <Teleport to="body">
    <Transition name="chat-assistant" mode="out-in">
      <button
        v-if="!open"
        key="launcher"
        type="button"
        class="chat-assistant-launcher"
        aria-haspopup="dialog"
        :aria-expanded="open"
        aria-controls="chat-assistant-dialog"
        @click="openChat"
      >
        <span class="chat-assistant-launcher__icon">
          <AppIcon name="sms" :size="24" />
          <span class="chat-assistant-launcher__pulse" aria-hidden="true"></span>
        </span>
        <span class="chat-assistant-launcher__copy">
          <strong>AI 安全助手</strong>
          <small>点击开始对话</small>
        </span>
      </button>

      <section
        v-else
        id="chat-assistant-dialog"
        key="dialog"
        class="chat-assistant-overlay"
        role="dialog"
        aria-modal="true"
        aria-labelledby="chat-assistant-title"
      >
        <header class="chat-assistant-header">
          <div class="chat-assistant-header__inner">
            <div class="chat-assistant-brand">
              <span class="chat-assistant-brand__icon"><AppIcon name="sms" :size="23" /></span>
              <div>
                <h2 id="chat-assistant-title">智能安全助手</h2>
                <p><span aria-hidden="true"></span>告警研判 · 设备排查 · 安全处置</p>
              </div>
            </div>
            <button type="button" class="chat-assistant-close" aria-label="关闭智能助手" title="关闭（Esc）" @click="closeChat">
              <AppIcon name="close" :size="22" />
            </button>
          </div>
        </header>

        <main class="chat-assistant-main">
          <div class="chat-wrap">
            <div ref="logEl" class="chat-log" role="log" aria-live="polite" aria-label="对话记录">
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

              <div v-if="sending" class="msg msg-bot msg-thinking" aria-label="智能助手正在回答">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                <span class="thinking-text">正在生成安全建议</span>
              </div>
            </div>

            <div class="chat-assistant-composer">
              <div class="chat-quick" aria-label="常用问题">
                <button
                  v-for="question in CHAT_QUICK"
                  :key="question"
                  type="button"
                  :disabled="sending"
                  @click="send(question)"
                >
                  {{ question }}
                </button>
              </div>
              <form class="chat-input" @submit.prevent="send()">
                <input
                  ref="inputEl"
                  v-model="input"
                  type="text"
                  placeholder="问我：报警流程 / 怎么疏散 / 阈值怎么设…"
                  autocomplete="off"
                  :disabled="sending"
                />
                <button
                  type="button"
                  class="btn-primary btn-mic"
                  :class="{ listening }"
                  :disabled="sending"
                  title="语音输入"
                  aria-label="语音输入"
                  @click="toggle()"
                >
                  <AppIcon name="mic" :size="16" />
                </button>
                <button type="submit" class="btn-primary" :disabled="sending || !input.trim()">
                  {{ sending ? '回答中' : '发送' }}
                </button>
              </form>
              <p class="chat-assistant-disclaimer">智能建议仅供辅助研判；紧急情况下请立即疏散并拨打 119。</p>
            </div>
          </div>
        </main>
      </section>
    </Transition>
  </Teleport>
</template>
