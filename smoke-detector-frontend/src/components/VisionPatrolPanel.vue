<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as api from '@/api'
import type {
  VisionEvent,
  VisionReviewVerdict,
  VisionStatus,
  VisionSummary,
} from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { fmtDate } from '@/utils/format'

const store = useDashboardStore()

const status = ref<VisionStatus | null>(null)
const summary = ref<VisionSummary>({ pendingReview: 0, confirmedFire: 0, falseAlarm: 0, total: 0 })
const events = ref<VisionEvent[]>([])
const loading = ref(true)
const actionBusy = ref(false)
const loadError = ref('')
const reviewTargetId = ref<number | null>(null)
const reviewVerdict = ref<VisionReviewVerdict>('CONFIRMED_FIRE')
const reviewRemark = ref('')
let pollTimer: number | undefined

const frame = computed(() => status.value?.currentFrame ?? null)
const analysis = computed(() => status.value?.latestAnalysis ?? null)
const frameImage = computed(() => frame.value?.imageUrl ?? '')
const pendingEvent = computed(() => events.value.find((event) => event.status === 'PENDING_REVIEW') ?? null)
const reviewTarget = computed(() => events.value.find((event) => event.id === reviewTargetId.value) ?? null)
const recentEvents = computed(() => events.value.slice(0, 6))
const modeLabel = computed(() => {
  if (!status.value?.enabled) return '巡检已停用'
  if (status.value.deepSeekConfigured) return `DeepSeek · ${status.value.model}`
  return '模拟规则降级（未配置 DeepSeek 密钥）'
})

function percent(value?: number | null): string {
  return value == null ? '—' : `${Math.round(Number(value) * 100)}%`
}

function eventStatusLabel(value: string): string {
  if (value === 'PENDING_REVIEW') return '待人工判断'
  if (value === 'CONFIRMED_FIRE') return '已确认为火情'
  if (value === 'FALSE_ALARM') return '已排除误报'
  return value
}

function dingTalkLabel(value: string): string {
  if (value === 'SENT') return '钉钉已推送'
  if (value === 'FAILED') return '钉钉推送失败'
  if (value === 'SKIPPED') return '钉钉未配置'
  return '钉钉发送中'
}

async function load(showError = false): Promise<void> {
  try {
    const [nextStatus, page, nextSummary] = await Promise.all([
      api.fetchVisionStatus(),
      api.fetchVisionEvents(),
      api.fetchVisionSummary(),
    ])
    status.value = nextStatus
    events.value = page.records ?? []
    summary.value = nextSummary
    loadError.value = ''
  } catch (error) {
    loadError.value = (error as Error).message
    if (showError) store.showToast(`AI 视觉巡检加载失败：${loadError.value}`, 'error')
  } finally {
    loading.value = false
  }
}

async function setPatrolRunning(running: boolean): Promise<void> {
  if (actionBusy.value || status.value?.running === running) return
  actionBusy.value = true
  try {
    status.value = running
      ? await api.startVisionPatrol()
      : await api.pauseVisionPatrol()
    await load()
    store.showToast(
      running
        ? 'AI 视觉巡检已开始，将持续自动识别，直到你点击暂停。'
        : 'AI 视觉巡检已暂停，不会继续自动识别或发送新的钉钉告警。',
      'success',
    )
  } catch (error) {
    store.showToast(`巡检状态切换失败：${(error as Error).message}`, 'error')
  } finally {
    actionBusy.value = false
  }
}

function openReview(event: VisionEvent, verdict: VisionReviewVerdict): void {
  reviewTargetId.value = event.id
  reviewVerdict.value = verdict
  reviewRemark.value = ''
}

async function submitReview(): Promise<void> {
  const target = reviewTarget.value
  const remark = reviewRemark.value.trim()
  if (!target || actionBusy.value) return
  if (!remark) {
    store.showToast('请填写现场核验或人工判断依据。', 'error')
    return
  }
  actionBusy.value = true
  try {
    await api.reviewVisionEvent(target.id, reviewVerdict.value, remark)
    reviewTargetId.value = null
    await load()
    store.showToast(
      reviewVerdict.value === 'CONFIRMED_FIRE'
        ? '已人工确认为火情，复核结果已留痕并通知钉钉。'
        : '已排除本次疑似火情，复核结果已留痕。',
      'success',
    )
  } catch (error) {
    store.showToast(`人工判断提交失败：${(error as Error).message}`, 'error')
  } finally {
    actionBusy.value = false
  }
}

onMounted(() => {
  void load(true)
  pollTimer = window.setInterval(() => void load(), 3000)
})

onBeforeUnmount(() => window.clearInterval(pollTimer))
</script>

<template>
  <section class="vision-patrol panel" aria-label="AI 视觉实时巡检">
    <div class="vision-patrol__head">
      <div>
        <span class="role-workspace__eyebrow">AI LIVE VISION PATROL</span>
        <h3>AI 视觉实时巡检</h3>
        <p>点击开始后立即识别并持续随机轮换 15 张模拟监控画面，直到点击暂停。</p>
      </div>
      <div class="vision-patrol__head-actions">
        <span class="vision-patrol__live" :class="{ scanning: status?.scanning, paused: !status?.running }"><i></i>{{ status?.scanning ? 'AI 分析中' : status?.running ? '巡检运行中' : '巡检已暂停' }}</span>
        <button v-if="store.canReviewVision && !status?.running" type="button" class="btn-primary" :disabled="actionBusy || !status?.enabled" @click="setPatrolRunning(true)">
          {{ actionBusy ? '处理中…' : '开始巡检' }}
        </button>
        <button v-if="store.canReviewVision && status?.running" type="button" class="btn-ghost" :disabled="actionBusy" @click="setPatrolRunning(false)">
          {{ actionBusy ? '处理中…' : '暂停巡检' }}
        </button>
      </div>
    </div>

    <div v-if="loading && !status" class="vision-patrol__empty">正在启动视觉巡检…</div>
    <div v-else-if="loadError && !status" class="vision-patrol__empty">视觉巡检暂不可用：{{ loadError }}</div>
    <template v-else>
      <div class="vision-patrol__mode" :class="{ fallback: !status?.deepSeekConfigured }">
        <strong>{{ modeLabel }}</strong>
        <span>15 张画面随机不重复一轮 · {{ status?.running ? `每 ${Math.round((status?.intervalMs ?? 15000) / 1000)} 秒分析一帧` : '自动识别已暂停' }}</span>
        <small v-if="!status?.running">暂停期间不会自动分析，也不会产生新的钉钉告警。</small>
        <small v-else-if="!status?.deepSeekConfigured">配置 DEEPSEEK_API_KEY 后自动切换为真实图片模型分析；当前结果来自预设演示规则。</small>
        <small v-else>图片正通过 DeepSeek Vision 分析；模型输出仍须人工现场核验。</small>
      </div>

      <div class="vision-patrol__grid">
        <figure class="vision-patrol__frame" :class="{ danger: analysis?.suspectedFire }">
          <Transition name="vision-frame" mode="out-in">
            <img v-if="frameImage" :key="frame?.frameKey" :src="frameImage" :alt="`${frame?.location ?? ''}模拟监控画面`" />
            <div v-else key="empty" class="vision-patrol__frame-empty">等待第一帧画面…</div>
          </Transition>
          <div class="vision-patrol__frame-top">
            <span><i></i>SIM LIVE</span><code>{{ frame?.cameraCode ?? '等待机位' }}</code>
          </div>
          <div v-if="analysis?.suspectedFire" class="vision-patrol__detection-box">
            <span>疑似火灾 {{ percent(analysis.confidence) }}</span>
          </div>
          <figcaption>
            <strong>{{ frame?.location ?? '模拟社区巡检' }}</strong>
            <time>{{ frame?.capturedAt ? fmtDate(frame.capturedAt) : '等待画面' }}</time>
          </figcaption>
        </figure>

        <div class="vision-patrol__analysis">
          <div class="vision-patrol__analysis-head">
            <span>最新 AI 分析</span>
            <i :class="analysis?.suspectedFire ? 'danger' : analysis?.error ? 'error' : 'safe'">
              {{ analysis?.suspectedFire ? '疑似火灾' : analysis?.error ? '分析失败' : analysis ? '未见异常' : '等待分析' }}
            </i>
          </div>
          <div class="vision-patrol__score">
            <div><strong>{{ percent(analysis?.confidence) }}</strong><span>模型置信度</span></div>
            <div><strong>{{ analysis?.riskLevel ?? '—' }}</strong><span>风险等级</span></div>
            <div><strong>{{ summary.pendingReview }}</strong><span>待人工判断</span></div>
          </div>
          <section>
            <h4>分析结论</h4><p>{{ analysis?.summary ?? '等待模拟画面进入分析队列。' }}</p>
          </section>
          <section>
            <h4>可见依据</h4><p>{{ analysis?.evidence ?? '—' }}</p>
          </section>
          <p v-if="analysis?.error" class="vision-patrol__error">DeepSeek 调用错误：{{ analysis.error }}</p>
          <small class="vision-patrol__analyzed-at">{{ analysis?.analyzedAt ? `分析时间：${fmtDate(analysis.analyzedAt)}` : '尚无分析记录' }}</small>
        </div>
      </div>

      <div v-if="pendingEvent" class="vision-patrol__pending">
        <div class="vision-patrol__pending-main">
          <span>等待人工判断</span>
          <strong>{{ pendingEvent.eventNo }} · {{ pendingEvent.location }}</strong>
          <p>{{ pendingEvent.summary }}；依据：{{ pendingEvent.evidence }}</p>
          <small :class="`notice-${pendingEvent.dingtalkStatus.toLowerCase()}`">
            {{ dingTalkLabel(pendingEvent.dingtalkStatus) }}<template v-if="pendingEvent.dingtalkRecipients"> · {{ pendingEvent.dingtalkRecipients }} 人</template>
          </small>
        </div>
        <div v-if="store.canReviewVision" class="vision-patrol__pending-actions">
          <button type="button" class="btn-primary" @click="openReview(pendingEvent, 'CONFIRMED_FIRE')">确认为火情</button>
          <button type="button" class="btn-ghost" @click="openReview(pendingEvent, 'FALSE_ALARM')">排除误报</button>
        </div>
      </div>

      <div class="vision-patrol__events">
        <div class="vision-patrol__events-head">
          <strong>最近视觉事件</strong>
          <span>待判断 {{ summary.pendingReview }} · 已确认 {{ summary.confirmedFire }} · 已排除 {{ summary.falseAlarm }}</span>
        </div>
        <div v-if="!recentEvents.length" class="vision-patrol__events-empty">尚未发现疑似火灾事件。</div>
        <div v-else class="vision-patrol__events-list">
          <article v-for="event in recentEvents" :key="event.id">
            <span class="vision-event-status" :class="`vision-event-status--${event.status.toLowerCase()}`">{{ eventStatusLabel(event.status) }}</span>
            <strong>{{ event.eventNo }}</strong>
            <p>{{ event.location }} · {{ percent(event.confidence) }} · {{ event.detectionMode === 'DEEPSEEK_VISION' ? 'DeepSeek' : '模拟规则' }}</p>
            <small>{{ fmtDate(event.createdAt) }} · {{ dingTalkLabel(event.dingtalkStatus) }}</small>
          </article>
        </div>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="reviewTarget" class="modal" @click.self="reviewTargetId = null">
        <div class="modal-card vision-patrol__modal" role="dialog" aria-modal="true" aria-labelledby="vision-review-title">
          <div class="modal-head">
            <h3 id="vision-review-title">人工判断视觉事件</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="reviewTargetId = null">×</button>
          </div>
          <form class="modal-form" @submit.prevent="submitReview">
            <p class="vision-patrol__modal-event">{{ reviewTarget.eventNo }} · {{ reviewTarget.location }}</p>
            <label>人工结论
              <select v-model="reviewVerdict">
                <option value="CONFIRMED_FIRE">确认为火情</option>
                <option value="FALSE_ALARM">排除误报</option>
              </select>
            </label>
            <label>判断依据
              <textarea v-model="reviewRemark" rows="6" maxlength="500" placeholder="填写现场电话核验、人员巡查或画面复核依据（必填）" required></textarea>
            </label>
            <p class="vision-patrol__modal-note">提交后将记录当前账号和判断时间，并向钉钉发送人工复核结果；结论不可反复覆盖。</p>
            <div class="modal-actions">
              <button type="button" class="btn-ghost" @click="reviewTargetId = null">取消</button>
              <button type="submit" class="btn-primary" :disabled="actionBusy">{{ actionBusy ? '提交中…' : '确认提交' }}</button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </section>
</template>
