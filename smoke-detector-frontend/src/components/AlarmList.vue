<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ALARM_SEVERITY, ALARM_STATUS, ALARM_TYPE, ALARM_UNIT } from '@/constants'
import type { AlarmStatus, AlarmType } from '@/constants'
import type { Alarm } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { conc, fmtDate } from '@/utils/format'

const store = useDashboardStore()

type TypeFilter = 'ALL' | AlarmType
type StatusFilter = 'ALL' | AlarmStatus

const typeFilter = ref<TypeFilter>('ALL')
const statusFilter = ref<StatusFilter>('ALL')
const visibleCount = ref(20)

const TYPE_FILTERS: { key: TypeFilter; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'SMOKE', label: '烟雾' },
  { key: 'TEMPERATURE', label: '温度' },
  { key: 'HUMIDITY', label: '湿度' },
  { key: 'CURRENT', label: '电流' },
  { key: 'WIRE_TEMPERATURE', label: '线温' },
  { key: 'CO', label: '一氧化碳' },
  { key: 'OFFLINE', label: '离线' },
]

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ALL', label: '全部状态' },
  { key: 'pending', label: '待处理' },
  { key: 'confirmed', label: '已确认' },
  { key: 'resolved', label: '已处置' },
  { key: 'false_alarm', label: '误报' },
]

const filtered = computed(() =>
  store.alarms.filter((alarm) => {
    if (typeFilter.value !== 'ALL' && alarm.alarmType !== typeFilter.value) return false
    if (statusFilter.value !== 'ALL' && alarm.status !== statusFilter.value) return false
    return true
  }),
)

const visible = computed(() => filtered.value.slice(0, visibleCount.value))
const hasMore = computed(() => filtered.value.length > visibleCount.value)

watch([typeFilter, statusFilter], () => {
  visibleCount.value = 20
})

function isActive(alarm: Alarm): boolean {
  return alarm.status === 'pending' || alarm.status === 'confirmed'
}

function badgeStyle(color: string): Record<string, string> {
  return {
    color,
    borderColor: `${color}55`,
    background: `${color}18`,
  }
}

function meta(alarm: Alarm): string {
  if (alarm.alarmType === 'OFFLINE') return '设备心跳超时，当前处于离线状态'
  const unit = ALARM_UNIT[alarm.alarmType]
  const value = `${conc(alarm.currentValue)}${unit}`
  return alarm.ruleDescription ? `${value} · ${alarm.ruleDescription}` : value
}
</script>

<template>
  <section id="panel-alarms" class="panel" aria-label="告警日志">
    <h2 class="panel-title">告警日志</h2>

    <div class="filter-row" role="group" aria-label="告警类型筛选">
      <button
        v-for="item in TYPE_FILTERS"
        :key="item.key"
        type="button"
        class="filter-chip"
        :class="{ active: typeFilter === item.key }"
        @click="typeFilter = item.key"
      >
        {{ item.label }}
      </button>
    </div>
    <div class="filter-row" role="group" aria-label="告警状态筛选">
      <button
        v-for="item in STATUS_FILTERS"
        :key="item.key"
        type="button"
        class="filter-chip"
        :class="{ active: statusFilter === item.key }"
        @click="statusFilter = item.key"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-if="visible.length" class="alarm-list">
      <div v-for="alarm in visible" :key="alarm.id" class="alarm-item">
        <div class="alarm-head">
          <span class="badge" :style="badgeStyle(ALARM_TYPE[alarm.alarmType]?.color ?? '#898781')">
            {{ ALARM_TYPE[alarm.alarmType]?.label ?? alarm.alarmType }}
          </span>
          <span class="badge" :style="badgeStyle(ALARM_STATUS[alarm.status]?.color ?? '#898781')">
            {{ ALARM_STATUS[alarm.status]?.label ?? alarm.status }}
          </span>
          <span
            v-if="alarm.severity"
            class="badge"
            :style="badgeStyle(ALARM_SEVERITY[alarm.severity]?.color ?? '#898781')"
          >
            {{ ALARM_SEVERITY[alarm.severity]?.label ?? alarm.severity }}
          </span>
        </div>
        <div class="alarm-device">{{ alarm.deviceCode }}</div>
        <div class="alarm-meta">{{ meta(alarm) }}</div>
        <div class="alarm-time">{{ fmtDate(alarm.createdAt) }}</div>
        <div v-if="isActive(alarm) && store.canHandleAlerts" class="alarm-actions">
          <button
            v-if="alarm.status === 'pending'"
            class="alarm-act"
            @click="store.handleAlarm(alarm.id, 'confirm')"
          >
            确认
          </button>
          <button class="alarm-act" @click="store.handleAlarm(alarm.id, 'resolve')">处置完成</button>
          <button class="alarm-act alarm-act-danger" @click="store.handleAlarm(alarm.id, 'false-alarm')">
            标记误报
          </button>
        </div>
        <button
          v-if="alarm.alarmType !== 'OFFLINE' && isActive(alarm) && store.canHandleAlerts"
          class="alarm-act alarm-act-verify"
          @click="store.verifyAlarm(alarm.id)"
        >
          告警复核
        </button>
        <div v-if="alarm.verifyResult" class="alarm-verify">{{ alarm.verifyResult }}</div>
      </div>

      <button v-if="hasMore" class="load-more" type="button" @click="visibleCount += 20">
        显示更多（还有 {{ filtered.length - visibleCount }} 条）
      </button>
    </div>
    <div v-else class="empty">暂无告警</div>
  </section>
</template>
