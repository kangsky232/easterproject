<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as api from '@/api'
import type { Notification, NotificationAuditResult } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { fmtDate } from '@/utils/format'

const store = useDashboardStore()

const channelFilter = ref('')
const statusFilter = ref('')
const auditFilter = ref('')
const keyword = ref('')
const attentionOnly = ref(false)
const selectedId = ref<number | null>(null)
const auditTargetId = ref<number | null>(null)
const auditResult = ref<NotificationAuditResult>('NORMAL')
const auditRemark = ref('')
const auditBusy = ref(false)
const refreshing = ref(false)

const filteredNotifications = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return store.notifications.filter((item) => {
    if (channelFilter.value && item.channel !== channelFilter.value) return false
    if (statusFilter.value && item.status !== statusFilter.value) return false
    if (auditFilter.value && item.auditStatus !== auditFilter.value) return false
    if (attentionOnly.value && !(item.status === 'FAILED' && item.auditStatus === 'PENDING')) return false
    if (!needle) return true
    return [item.deviceId, item.receiver, item.content, item.auditorUsername, item.auditRemark]
      .some((value) => String(value ?? '').toLowerCase().includes(needle))
  })
})

const selected = computed<Notification | null>(() => {
  const current = store.notifications.find((item) => item.id === selectedId.value)
  return current ?? filteredNotifications.value[0] ?? null
})

const auditTarget = computed(() =>
  store.notifications.find((item) => item.id === auditTargetId.value) ?? null,
)

const activeFilterCount = computed(() =>
  [channelFilter.value, statusFilter.value, auditFilter.value, keyword.value.trim(), attentionOnly.value]
    .filter(Boolean).length,
)

function channelLabel(channel: string): string {
  if (channel === 'SMS') return '短信通知'
  if (channel === 'DINGTALK') return '钉钉告警'
  return 'APP 通知'
}

function statusLabel(status?: string): string {
  if (status === 'SENT') return '已送达'
  if (status === 'FAILED') return '投递失败'
  if (status === 'PENDING') return '待发送'
  return status || '未知'
}

function auditLabel(status?: string): string {
  return status === 'COMPLETED' ? '已核查' : '待核查'
}

function auditResultLabel(result?: string | null): string {
  if (result === 'FOLLOWED_UP') return '已跟进处理'
  if (result === 'NORMAL') return '核查正常'
  return '—'
}

function selectNotification(id: number): void {
  selectedId.value = id
}

function clearFilters(): void {
  channelFilter.value = ''
  statusFilter.value = ''
  auditFilter.value = ''
  keyword.value = ''
  attentionOnly.value = false
}

function applyQuickFilter(type: 'all' | 'sent' | 'attention' | 'pendingAudit' | 'completedAudit'): void {
  clearFilters()
  if (type === 'sent') statusFilter.value = 'SENT'
  if (type === 'attention') attentionOnly.value = true
  if (type === 'pendingAudit') auditFilter.value = 'PENDING'
  if (type === 'completedAudit') auditFilter.value = 'COMPLETED'
}

async function refresh(): Promise<void> {
  refreshing.value = true
  try {
    await Promise.all([store.fetchNotifications(), store.fetchNotificationSummary()])
  } catch (error) {
    store.showToast(`通知审计刷新失败：${(error as Error).message}`, 'error')
  } finally {
    refreshing.value = false
  }
}

function openAudit(notification: Notification): void {
  auditTargetId.value = notification.id
  auditResult.value = notification.status === 'FAILED' ? 'FOLLOWED_UP' : 'NORMAL'
  auditRemark.value = ''
}

async function submitAudit(): Promise<void> {
  const target = auditTarget.value
  const remark = auditRemark.value.trim()
  if (!target) return
  if (!remark) {
    store.showToast('请填写核查结论或已采取的处理措施。', 'error')
    return
  }
  auditBusy.value = true
  try {
    await api.auditNotification(target.id, auditResult.value, remark)
    auditTargetId.value = null
    selectedId.value = target.id
    await refresh()
    store.showToast(`通知 #${target.id} 已完成核查并留痕。`, 'success')
  } catch (error) {
    store.showToast(`通知核查提交失败：${(error as Error).message}`, 'error')
  } finally {
    auditBusy.value = false
  }
}

function csvCell(value: unknown): string {
  let text = String(value ?? '')
  if (/^[=+\-@]/.test(text)) text = `'${text}`
  return `"${text.replace(/"/g, '""')}"`
}

function exportCsv(): void {
  const rows = filteredNotifications.value.map((item) => [
    item.id,
    item.alertId,
    item.deviceId,
    channelLabel(item.channel),
    item.receiver,
    statusLabel(item.status),
    auditLabel(item.auditStatus),
    auditResultLabel(item.auditResult),
    item.auditorUsername,
    item.auditRemark,
    item.content,
    item.createdAt,
    item.sentAt,
    item.auditedAt,
  ])
  const headers = ['记录编号', '告警编号', '设备', '通知渠道', '接收对象', '投递状态', '核查状态', '核查结果', '核查人', '核查结论', '通知内容', '创建时间', '送达时间', '核查时间']
  const content = [headers, ...rows].map((row) => row.map(csvCell).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `通知审计-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  store.showToast(`已导出 ${rows.length} 条当前筛选记录。`, 'success')
}

watch(
  filteredNotifications,
  (items) => {
    if (!items.some((item) => item.id === selectedId.value)) selectedId.value = items[0]?.id ?? null
  },
  { immediate: true },
)
</script>

<template>
  <section class="notification-audit">
    <div class="notification-audit__head">
      <div>
        <span class="role-workspace__eyebrow">NOTIFICATION AUDIT</span>
        <h2>通知投递与核查</h2>
        <p>定位投递异常，记录核查责任人与处理结论，让每条通知都有可追踪的结果。</p>
      </div>
      <div class="notification-audit__head-actions">
        <button type="button" class="btn-ghost" :disabled="refreshing" @click="refresh">
          {{ refreshing ? '刷新中…' : '刷新数据' }}
        </button>
        <button type="button" class="btn-primary" :disabled="!filteredNotifications.length" @click="exportCsv">
          导出当前结果
        </button>
      </div>
    </div>

    <p class="notification-audit__notice">
      审计核查表示工作人员已检查这条投递记录，不代表短信或钉钉接收人已经阅读消息。短信通道未接入运营商时会保持“待发送”。
    </p>

    <div class="notification-audit__summary" aria-label="通知审计统计">
      <button type="button" :class="{ active: !statusFilter && !auditFilter && !attentionOnly }" @click="applyQuickFilter('all')">
        <span>全部记录</span><strong>{{ store.notificationSummary.total }}</strong><small>最近展示 200 条</small>
      </button>
      <button type="button" :class="{ active: statusFilter === 'SENT' }" @click="applyQuickFilter('sent')">
        <span>已送达</span><strong>{{ store.notificationSummary.sentCount }}</strong><small>投递完成</small>
      </button>
      <button type="button" class="notification-audit__summary-alert" :class="{ active: attentionOnly }" @click="applyQuickFilter('attention')">
        <span>异常待办</span><strong>{{ store.notificationSummary.attentionCount }}</strong><small>失败且未核查</small>
      </button>
      <button type="button" :class="{ active: auditFilter === 'PENDING' }" @click="applyQuickFilter('pendingAudit')">
        <span>待核查</span><strong>{{ store.notificationSummary.pendingAuditCount }}</strong><small>需要人工确认</small>
      </button>
      <button type="button" :class="{ active: auditFilter === 'COMPLETED' }" @click="applyQuickFilter('completedAudit')">
        <span>已闭环</span><strong>{{ store.notificationSummary.completedAuditCount }}</strong><small>结论已留痕</small>
      </button>
    </div>

    <div class="notification-audit__filters">
      <label>渠道
        <select v-model="channelFilter">
          <option value="">全部渠道</option>
          <option value="APP">APP 通知</option>
          <option value="SMS">短信通知</option>
          <option value="DINGTALK">钉钉告警</option>
        </select>
      </label>
      <label>投递状态
        <select v-model="statusFilter" @change="attentionOnly = false">
          <option value="">全部状态</option>
          <option value="SENT">已送达</option>
          <option value="PENDING">待发送</option>
          <option value="FAILED">投递失败</option>
        </select>
      </label>
      <label>核查状态
        <select v-model="auditFilter" @change="attentionOnly = false">
          <option value="">全部状态</option>
          <option value="PENDING">待核查</option>
          <option value="COMPLETED">已核查</option>
        </select>
      </label>
      <label class="notification-audit__search">搜索
        <input v-model="keyword" maxlength="100" placeholder="设备、接收人、通知内容或核查结论" />
      </label>
      <button v-if="activeFilterCount" type="button" class="btn-ghost" @click="clearFilters">清除筛选（{{ activeFilterCount }}）</button>
    </div>

    <div class="notification-audit__layout">
      <aside class="notification-audit__list panel" aria-label="通知记录列表">
        <div class="notification-audit__list-title">
          <strong>通知记录</strong><span>{{ filteredNotifications.length }} 条</span>
        </div>
        <div v-if="!filteredNotifications.length" class="notification-audit__empty">当前筛选条件下暂无通知记录。</div>
        <button
          v-for="item in filteredNotifications"
          v-else
          :key="item.id"
          type="button"
          class="notification-audit__item"
          :class="{ active: selected?.id === item.id, attention: item.status === 'FAILED' && item.auditStatus === 'PENDING' }"
          @click="selectNotification(item.id)"
        >
          <span class="notification-audit__item-top">
            <strong>#{{ item.id }} · {{ channelLabel(item.channel) }}</strong>
            <time>{{ fmtDate(item.createdAt) }}</time>
          </span>
          <span class="notification-audit__item-device">设备 {{ item.deviceId || '—' }} · 告警 #{{ item.alertId }}</span>
          <span class="notification-audit__item-content">{{ item.content || '无通知内容' }}</span>
          <span class="notification-audit__badges">
            <i class="notification-delivery" :class="`notification-delivery--${String(item.status).toLowerCase()}`">{{ statusLabel(item.status) }}</i>
            <i class="notification-review" :class="`notification-review--${item.auditStatus.toLowerCase()}`">{{ auditLabel(item.auditStatus) }}</i>
          </span>
        </button>
      </aside>

      <article v-if="selected" class="notification-audit__detail panel">
        <div class="notification-audit__detail-head">
          <div><code>NOTIFY-{{ selected.id }}</code><h3>{{ channelLabel(selected.channel) }}投递详情</h3></div>
          <span class="notification-review" :class="`notification-review--${selected.auditStatus.toLowerCase()}`">{{ auditLabel(selected.auditStatus) }}</span>
        </div>

        <ol class="notification-audit__progress" aria-label="通知审计进度">
          <li class="done"><i>✓</i><span>记录创建</span><small>{{ fmtDate(selected.createdAt) }}</small></li>
          <li :class="{ done: selected.status === 'SENT', failed: selected.status === 'FAILED', pending: selected.status === 'PENDING' }">
            <i>{{ selected.status === 'SENT' ? '✓' : selected.status === 'FAILED' ? '!' : '2' }}</i>
            <span>{{ statusLabel(selected.status) }}</span><small>{{ selected.sentAt ? fmtDate(selected.sentAt) : '等待通道结果' }}</small>
          </li>
          <li :class="{ done: selected.auditStatus === 'COMPLETED', pending: selected.auditStatus === 'PENDING' }">
            <i>{{ selected.auditStatus === 'COMPLETED' ? '✓' : '3' }}</i><span>{{ auditLabel(selected.auditStatus) }}</span>
            <small>{{ selected.auditedAt ? fmtDate(selected.auditedAt) : '等待工作人员核查' }}</small>
          </li>
        </ol>

        <dl class="notification-audit__info">
          <div><dt>关联告警</dt><dd>#{{ selected.alertId }}</dd></div>
          <div><dt>目标设备</dt><dd>{{ selected.deviceId || '—' }}</dd></div>
          <div><dt>接收对象</dt><dd>{{ selected.receiver || '—' }}</dd></div>
          <div><dt>通知渠道</dt><dd>{{ channelLabel(selected.channel) }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ fmtDate(selected.createdAt) }}</dd></div>
          <div><dt>送达时间</dt><dd>{{ selected.sentAt ? fmtDate(selected.sentAt) : '—' }}</dd></div>
        </dl>

        <section class="notification-audit__content">
          <h4>通知内容</h4><p>{{ selected.content || '无通知内容' }}</p>
        </section>

        <section v-if="selected.auditStatus === 'COMPLETED'" class="notification-audit__result">
          <div><h4>核查结论</h4><span>{{ auditResultLabel(selected.auditResult) }}</span></div>
          <p>{{ selected.auditRemark }}</p>
          <small>核查人：{{ selected.auditorUsername }} · {{ fmtDate(selected.auditedAt) }}</small>
        </section>
        <section v-else class="notification-audit__pending-card">
          <div><strong>这条记录尚未完成人工核查</strong><p>确认渠道状态和接收配置后，填写结论即可闭环归档。</p></div>
          <button v-if="store.canAuditNotifications" type="button" class="btn-primary" @click="openAudit(selected)">填写核查结论</button>
        </section>
      </article>
      <div v-else class="notification-audit__detail notification-audit__empty panel">选择一条通知查看投递与核查详情。</div>
    </div>

    <Teleport to="body">
      <div v-if="auditTarget" class="modal" @click.self="auditTargetId = null">
        <div class="modal-card notification-audit__modal" role="dialog" aria-modal="true" aria-labelledby="notification-audit-title">
          <div class="modal-head">
            <h3 id="notification-audit-title">填写核查结论</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="auditTargetId = null">×</button>
          </div>
          <form class="modal-form" @submit.prevent="submitAudit">
            <p class="notification-audit__modal-target">通知 #{{ auditTarget.id }} · {{ channelLabel(auditTarget.channel) }} · {{ statusLabel(auditTarget.status) }}</p>
            <label>核查结果
              <select v-model="auditResult">
                <option value="NORMAL">核查正常</option>
                <option value="FOLLOWED_UP">已跟进处理</option>
              </select>
            </label>
            <label>核查结论
              <textarea v-model="auditRemark" rows="6" maxlength="500" placeholder="说明核查依据、接收配置或已采取的处理措施（必填）" required></textarea>
            </label>
            <p class="notification-audit__modal-note">提交后将记录当前账号和完成时间，审计结论不可反复覆盖。</p>
            <div class="modal-actions">
              <button type="button" class="btn-ghost" @click="auditTargetId = null">取消</button>
              <button type="submit" class="btn-primary" :disabled="auditBusy">{{ auditBusy ? '提交中…' : '确认完成核查' }}</button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </section>
</template>
