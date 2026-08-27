<script setup lang="ts">
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'
import { fmtDate } from '@/utils/format'

const store = useDashboardStore()

function channelLabel(channel: string): string {
  if (channel === 'SMS') return '短信通知'
  if (channel === 'DINGTALK') return '钉钉告警'
  return 'APP 通知'
}

function statusLabel(status?: string): string {
  if (status === 'SENT') return '已送达'
  if (status === 'FAILED') return '失败'
  if (status === 'PENDING') return '待发送'
  return status || '未知'
}
</script>

<template>
  <h2 class="section-title">通知记录（APP / 短信 / 钉钉）</h2>
  <div v-if="store.notifications.length" class="notification-list">
    <div v-for="(notification, index) in store.notifications" :key="index" class="notification-item">
      <span class="notif-icon" aria-hidden="true">
        <AppIcon :name="notification.channel === 'SMS' ? 'sms' : 'phone'" :size="20" />
      </span>
      <div class="notif-main">
        <div class="notif-title">
          {{ channelLabel(notification.channel) }} · {{ notification.receiver }}
        </div>
        <div class="notif-sub">
          设备 {{ notification.deviceId }} · {{ notification.content }} · 状态：{{ statusLabel(notification.status) }}
        </div>
      </div>
      <div class="notif-time">{{ fmtDate(notification.sentAt || notification.createdAt) }}</div>
    </div>
  </div>
  <div v-else class="empty">暂无通知记录</div>
</template>
