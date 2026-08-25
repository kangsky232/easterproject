<script setup lang="ts">
import { BROADCAST_STATUS } from '@/constants'
import { useDashboardStore } from '@/store/dashboard'
import { fmtDate } from '@/utils/format'

const store = useDashboardStore()

function statusClass(status: number): string {
  if (status === 1) return 'broadcast-status--success'
  if (status === 2) return 'broadcast-status--failed'
  return 'broadcast-status--pending'
}
</script>

<template>
  <div class="section-head">
    <h2 class="section-title">广播指令记录</h2>
  </div>

  <p v-if="store.broadcastPersistenceOnly" class="capability-note capability-note--page">
    当前广播能力为“仅保存记录”：待下发表示指令已创建，不表示设备已经收到或播放。
  </p>

  <div class="table-wrap">
    <table class="device-table broadcast-table">
      <thead>
        <tr>
          <th>编号</th>
          <th>目标设备</th>
          <th>广播内容</th>
          <th>关联告警</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>执行时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!store.broadcasts.length">
          <td colspan="7" class="empty">暂无广播指令记录。</td>
        </tr>
        <tr v-for="broadcast in store.broadcasts" v-else :key="broadcast.id">
          <td>#{{ broadcast.id }}</td>
          <td>{{ broadcast.deviceId }}</td>
          <td class="broadcast-content">{{ broadcast.content }}</td>
          <td>{{ broadcast.triggerAlertId ? `#${broadcast.triggerAlertId}` : '—' }}</td>
          <td>
            <span class="broadcast-status" :class="statusClass(broadcast.status)">
              {{ BROADCAST_STATUS[broadcast.status] ?? `未知（${broadcast.status}）` }}
            </span>
          </td>
          <td>{{ fmtDate(broadcast.createdAt) }}</td>
          <td>{{ broadcast.executedAt ? fmtDate(broadcast.executedAt) : '—' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
