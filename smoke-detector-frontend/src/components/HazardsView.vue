<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as api from '@/api'
import type {
  HazardActionType,
  HazardDetail,
  HazardPriority,
  HazardStatus,
  HazardTicket,
} from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { fmtDate } from '@/utils/format'

const props = defineProps<{ active: boolean }>()
const store = useDashboardStore()

const STATUS_META: Record<HazardStatus, { label: string; step: number }> = {
  REPORTED: { label: '待接单', step: 0 },
  PROCESSING: { label: '整改中', step: 1 },
  PENDING_REVIEW: { label: '待复核', step: 2 },
  CLOSED: { label: '已闭环', step: 3 },
}
const PRIORITY_META: Record<HazardPriority, string> = {
  LOW: '低',
  MEDIUM: '一般',
  HIGH: '高',
  URGENT: '紧急',
}
const ACTION_LABEL: Record<HazardActionType, string> = {
  REPORTED: '隐患上报',
  CLAIMED: '接单整改',
  SUBMITTED: '提交复核',
  APPROVED: '复核通过',
  REJECTED: '复核驳回',
}
const WORKFLOW_STEPS = ['发现上报', '整改处理', '等待复核', '完成闭环']

const tickets = ref<HazardTicket[]>([])
const detail = ref<HazardDetail | null>(null)
const selectedId = ref<number | null>(null)
const statusFilter = ref<HazardStatus | ''>('')
const priorityFilter = ref<HazardPriority | ''>('')
const loading = ref(false)
const actionBusy = ref(false)
const createOpen = ref(false)
const actionMode = ref<'submit' | 'approve' | 'reject' | null>(null)
const actionRemark = ref('')
const createForm = ref({
  title: '',
  description: '',
  location: '',
  priority: 'MEDIUM' as HazardPriority,
})

const currentTicket = computed(() => detail.value?.ticket ?? null)
const currentStep = computed(() => currentTicket.value ? STATUS_META[currentTicket.value.status].step : 0)
const currentUsername = computed(() => String(store.currentUser?.username ?? ''))
const canClaim = computed(
  () => store.canHandleHazards && currentTicket.value?.status === 'REPORTED',
)
const canSubmit = computed(() => {
  const ticket = currentTicket.value
  if (!ticket || ticket.status !== 'PROCESSING' || !store.canHandleHazards) return false
  return ticket.assigneeUsername === currentUsername.value || store.canReviewHazards
})
const canReview = computed(
  () => store.canReviewHazards && currentTicket.value?.status === 'PENDING_REVIEW',
)
const actionTitle = computed(() => {
  if (actionMode.value === 'submit') return '提交整改结果'
  if (actionMode.value === 'approve') return '复核通过并关闭'
  return '驳回并要求重做'
})
const actionPlaceholder = computed(() => {
  if (actionMode.value === 'submit') return '填写已采取的整改措施和现场结果（必填）'
  if (actionMode.value === 'reject') return '填写未通过原因和需要补充的整改要求（必填）'
  return '填写复核说明（可选）'
})

function statusLabel(status: HazardStatus): string {
  return STATUS_META[status]?.label ?? status
}

function priorityLabel(priority: HazardPriority): string {
  return PRIORITY_META[priority] ?? priority
}

async function loadTickets(preferredId?: number): Promise<void> {
  loading.value = true
  try {
    const page = await api.fetchHazards({ status: statusFilter.value, priority: priorityFilter.value })
    tickets.value = page.records ?? []
    const nextId = preferredId && tickets.value.some((item) => item.id === preferredId)
      ? preferredId
      : selectedId.value && tickets.value.some((item) => item.id === selectedId.value)
        ? selectedId.value
        : tickets.value[0]?.id ?? null
    selectedId.value = nextId
    detail.value = nextId ? await api.fetchHazard(nextId) : null
  } catch (error) {
    store.showToast(`隐患列表加载失败：${(error as Error).message}`, 'error')
  } finally {
    loading.value = false
  }
}

async function loadAll(preferredId?: number): Promise<void> {
  await Promise.all([loadTickets(preferredId), store.fetchHazardSummary()])
}

async function selectTicket(id: number): Promise<void> {
  selectedId.value = id
  try {
    detail.value = await api.fetchHazard(id)
  } catch (error) {
    store.showToast(`隐患详情加载失败：${(error as Error).message}`, 'error')
  }
}

function openCreate(): void {
  createForm.value = {
    title: '',
    description: '',
    location: store.selectedDevice?.location ?? '',
    priority: 'MEDIUM',
  }
  createOpen.value = true
}

async function createTicket(): Promise<void> {
  const payload = {
    title: createForm.value.title.trim(),
    description: createForm.value.description.trim(),
    location: createForm.value.location.trim(),
    priority: createForm.value.priority,
  }
  if (!payload.title || !payload.description || !payload.location) {
    store.showToast('请完整填写隐患标题、位置和情况说明。', 'error')
    return
  }
  actionBusy.value = true
  try {
    const created = await api.createHazard(payload)
    createOpen.value = false
    statusFilter.value = ''
    priorityFilter.value = ''
    await loadAll(created.id)
    store.showToast(`隐患 ${created.ticketNo} 已上报，可持续跟踪整改进度。`, 'success')
  } catch (error) {
    store.showToast(`隐患上报失败：${(error as Error).message}`, 'error')
  } finally {
    actionBusy.value = false
  }
}

async function claimTicket(): Promise<void> {
  const ticket = currentTicket.value
  if (!ticket) return
  const ok = await store.confirm(`确定接单处理“${ticket.title}”吗？接单后由你负责提交整改结果。`, '接单整改')
  if (!ok) return
  actionBusy.value = true
  try {
    await api.claimHazard(ticket.id)
    await loadAll(ticket.id)
    store.showToast(`已接单 ${ticket.ticketNo}，请完成整改后提交复核。`, 'success')
  } catch (error) {
    store.showToast(`接单失败：${(error as Error).message}`, 'error')
  } finally {
    actionBusy.value = false
  }
}

function openAction(mode: 'submit' | 'approve' | 'reject'): void {
  actionMode.value = mode
  actionRemark.value = ''
}

async function completeAction(): Promise<void> {
  const ticket = currentTicket.value
  const mode = actionMode.value
  const remark = actionRemark.value.trim()
  if (!ticket || !mode) return
  if ((mode === 'submit' || mode === 'reject') && !remark) {
    store.showToast(mode === 'submit' ? '请填写整改结果。' : '请填写驳回原因。', 'error')
    return
  }
  actionBusy.value = true
  try {
    if (mode === 'submit') await api.submitHazard(ticket.id, remark)
    else await api.reviewHazard(ticket.id, mode === 'approve', remark)
    actionMode.value = null
    await loadAll(ticket.id)
    const message = mode === 'submit'
      ? '整改结果已提交，等待管理员复核。'
      : mode === 'approve'
        ? '复核通过，隐患工单已完成闭环。'
        : '复核已驳回，工单返回整改中。'
    store.showToast(message, 'success')
  } catch (error) {
    store.showToast(`操作失败：${(error as Error).message}`, 'error')
  } finally {
    actionBusy.value = false
  }
}

function applyStatusFilter(status: HazardStatus | ''): void {
  statusFilter.value = status
}

watch(
  () => props.active,
  (active) => {
    if (active) void loadAll()
  },
  { immediate: true },
)

watch([statusFilter, priorityFilter], () => {
  if (props.active) void loadTickets()
})
</script>

<template>
  <section class="hazard-view">
    <div class="hazard-head">
      <div>
        <span class="role-workspace__eyebrow">HAZARD MANAGEMENT</span>
        <h2>安全隐患管理</h2>
        <p>从发现上报、接单整改到复核归档，全过程留痕并按角色流转。</p>
      </div>
      <button v-if="store.canReportHazards" type="button" class="btn-primary" @click="openCreate">＋ 上报隐患</button>
    </div>

    <div class="hazard-summary" aria-label="隐患状态统计">
      <button type="button" :class="{ active: statusFilter === 'REPORTED' }" @click="applyStatusFilter('REPORTED')">
        <span>待接单</span><strong>{{ store.hazardSummary.reported }}</strong>
      </button>
      <button type="button" :class="{ active: statusFilter === 'PROCESSING' }" @click="applyStatusFilter('PROCESSING')">
        <span>整改中</span><strong>{{ store.hazardSummary.processing }}</strong>
      </button>
      <button type="button" :class="{ active: statusFilter === 'PENDING_REVIEW' }" @click="applyStatusFilter('PENDING_REVIEW')">
        <span>待复核</span><strong>{{ store.hazardSummary.pendingReview }}</strong>
      </button>
      <button type="button" :class="{ active: statusFilter === 'CLOSED' }" @click="applyStatusFilter('CLOSED')">
        <span>已闭环</span><strong>{{ store.hazardSummary.closed }}</strong>
      </button>
    </div>

    <div class="hazard-filters">
      <label>状态
        <select v-model="statusFilter">
          <option value="">全部状态</option>
          <option value="REPORTED">待接单</option>
          <option value="PROCESSING">整改中</option>
          <option value="PENDING_REVIEW">待复核</option>
          <option value="CLOSED">已闭环</option>
        </select>
      </label>
      <label>优先级
        <select v-model="priorityFilter">
          <option value="">全部优先级</option>
          <option value="URGENT">紧急</option>
          <option value="HIGH">高</option>
          <option value="MEDIUM">一般</option>
          <option value="LOW">低</option>
        </select>
      </label>
      <button v-if="statusFilter || priorityFilter" type="button" class="btn-ghost" @click="statusFilter = ''; priorityFilter = ''">清除筛选</button>
      <button type="button" class="btn-ghost" :disabled="loading" @click="loadAll()">{{ loading ? '刷新中…' : '刷新' }}</button>
    </div>

    <div class="hazard-layout">
      <aside class="hazard-list panel" aria-label="隐患工单列表">
        <div v-if="loading && !tickets.length" class="hazard-empty">正在加载隐患工单…</div>
        <div v-else-if="!tickets.length" class="hazard-empty">当前筛选条件下暂无隐患工单。</div>
        <template v-else>
          <button
            v-for="item in tickets"
            :key="item.id"
            type="button"
            class="hazard-list__item"
            :class="{ active: selectedId === item.id }"
            @click="selectTicket(item.id)"
          >
            <span class="hazard-list__top">
              <code>{{ item.ticketNo }}</code>
              <i class="hazard-priority" :class="'hazard-priority--' + item.priority.toLowerCase()">{{ priorityLabel(item.priority) }}</i>
            </span>
            <strong>{{ item.title }}</strong>
            <span class="hazard-list__location">📍 {{ item.location }}</span>
            <span class="hazard-list__bottom">
              <em class="hazard-status" :class="'hazard-status--' + item.status.toLowerCase()">{{ statusLabel(item.status) }}</em>
              <time>{{ fmtDate(item.updatedAt) }}</time>
            </span>
          </button>
        </template>
      </aside>

      <article v-if="currentTicket" class="hazard-detail panel">
        <div class="hazard-detail__head">
          <div>
            <code>{{ currentTicket.ticketNo }}</code>
            <h3>{{ currentTicket.title }}</h3>
          </div>
          <div class="hazard-detail__badges">
            <span class="hazard-priority" :class="'hazard-priority--' + currentTicket.priority.toLowerCase()">{{ priorityLabel(currentTicket.priority) }}优先级</span>
            <span class="hazard-status" :class="'hazard-status--' + currentTicket.status.toLowerCase()">{{ statusLabel(currentTicket.status) }}</span>
          </div>
        </div>

        <ol class="hazard-progress" aria-label="闭环进度">
          <li v-for="(step, index) in WORKFLOW_STEPS" :key="step" :class="{ done: index <= currentStep, current: index === currentStep }">
            <i>{{ index < currentStep ? '✓' : index + 1 }}</i><span>{{ step }}</span>
          </li>
        </ol>

        <dl class="hazard-info">
          <div><dt>发生位置</dt><dd>{{ currentTicket.location }}</dd></div>
          <div><dt>上报人员</dt><dd>{{ currentTicket.reporterUsername }}</dd></div>
          <div><dt>整改人员</dt><dd>{{ currentTicket.assigneeUsername || '尚未接单' }}</dd></div>
          <div><dt>上报时间</dt><dd>{{ fmtDate(currentTicket.createdAt) }}</dd></div>
        </dl>

        <section class="hazard-description">
          <h4>隐患情况</h4>
          <p>{{ currentTicket.description }}</p>
        </section>
        <section v-if="currentTicket.resolution" class="hazard-description hazard-description--resolution">
          <h4>整改结果</h4>
          <p>{{ currentTicket.resolution }}</p>
        </section>

        <div v-if="canClaim || canSubmit || canReview" class="hazard-actions">
          <button v-if="canClaim" type="button" class="btn-primary" :disabled="actionBusy" @click="claimTicket">接单整改</button>
          <button v-if="canSubmit" type="button" class="btn-primary" :disabled="actionBusy" @click="openAction('submit')">提交整改结果</button>
          <template v-if="canReview">
            <button type="button" class="btn-primary" :disabled="actionBusy" @click="openAction('approve')">复核通过并关闭</button>
            <button type="button" class="btn-ghost hazard-reject" :disabled="actionBusy" @click="openAction('reject')">驳回重做</button>
          </template>
        </div>

        <section class="hazard-timeline">
          <h4>流转记录</h4>
          <ol>
            <li v-for="action in detail?.actions" :key="action.id" :class="'hazard-action--' + action.actionType.toLowerCase()">
              <i></i>
              <div>
                <span><strong>{{ ACTION_LABEL[action.actionType] }}</strong><time>{{ fmtDate(action.createdAt) }}</time></span>
                <p>{{ action.remark }}</p>
                <small>操作人：{{ action.operatorName }}</small>
              </div>
            </li>
          </ol>
        </section>
      </article>
      <div v-else class="hazard-detail hazard-empty panel">选择一条隐患工单查看完整闭环记录。</div>
    </div>

    <Teleport to="body">
      <div v-if="createOpen" class="modal" @click.self="createOpen = false">
        <div class="modal-card hazard-modal" role="dialog" aria-modal="true" aria-labelledby="hazard-create-title">
          <div class="modal-head">
            <h3 id="hazard-create-title">上报安全隐患</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="createOpen = false">×</button>
          </div>
          <form class="modal-form" @submit.prevent="createTicket">
            <label>隐患标题<input v-model="createForm.title" maxlength="100" placeholder="例如：消防通道堆放杂物" required /></label>
            <label>发生位置<input v-model="createForm.location" maxlength="200" placeholder="例如：2号楼5层西侧楼道" required /></label>
            <label>优先级
              <select v-model="createForm.priority">
                <option value="LOW">低</option>
                <option value="MEDIUM">一般</option>
                <option value="HIGH">高</option>
                <option value="URGENT">紧急</option>
              </select>
            </label>
            <label>情况说明<textarea v-model="createForm.description" rows="5" maxlength="1000" placeholder="说明发现的问题、影响范围和现场情况" required></textarea></label>
            <div class="modal-actions">
              <button type="button" class="btn-ghost" @click="createOpen = false">取消</button>
              <button type="submit" class="btn-primary" :disabled="actionBusy">{{ actionBusy ? '提交中…' : '确认上报' }}</button>
            </div>
          </form>
        </div>
      </div>

      <div v-if="actionMode" class="modal" @click.self="actionMode = null">
        <div class="modal-card hazard-modal" role="dialog" aria-modal="true" aria-labelledby="hazard-action-title">
          <div class="modal-head">
            <h3 id="hazard-action-title">{{ actionTitle }}</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="actionMode = null">×</button>
          </div>
          <form class="modal-form" @submit.prevent="completeAction">
            <p class="hazard-modal__ticket">{{ currentTicket?.ticketNo }} · {{ currentTicket?.title }}</p>
            <label>{{ actionMode === 'submit' ? '整改结果' : '复核说明' }}
              <textarea v-model="actionRemark" rows="6" maxlength="1000" :placeholder="actionPlaceholder"></textarea>
            </label>
            <div class="modal-actions">
              <button type="button" class="btn-ghost" @click="actionMode = null">取消</button>
              <button type="submit" class="btn-primary" :class="{ 'hazard-action-danger': actionMode === 'reject' }" :disabled="actionBusy">
                {{ actionBusy ? '处理中…' : '确认提交' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </section>
</template>
