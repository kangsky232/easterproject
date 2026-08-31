<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import { ROLE_LABEL } from '@/constants'
import { useClock } from '@/composables/useClock'
import AppIcon from '@/components/AppIcon.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

const store = useDashboardStore()
const now = useClock()

const emit = defineEmits<{
  (e: 'broadcast'): void
  (e: 'simulate'): void
  (e: 'show-alarms'): void
  (e: 'home'): void
}>()

const pad = (n: number) => String(n).padStart(2, '0')

const clockText = computed(() => {
  const date = now.value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
})

const userName = computed(() => store.currentUser?.displayName || store.currentUser?.username || '未登录')
const roleLabel = computed(() => ROLE_LABEL[store.currentUser?.role ?? ''] ?? '')
</script>

<template>
  <header class="topbar">
    <button type="button" class="brand brand--clickable" aria-label="返回首页" @click="emit('home')">
      <span class="logo" aria-hidden="true"><AppIcon name="flame" :size="30" /></span>
      <div class="brand-text">
        <h1>智慧烟感预警系统</h1>
        <p class="subtitle">Smart Smoke Detector · 实时监控大屏</p>
      </div>
    </button>

    <div class="topbar-right">
      <button
        v-if="store.canBroadcast"
        class="test-btn"
        :title="store.broadcastPersistenceOnly ? '创建广播指令记录' : '联动广播'"
        @click="emit('broadcast')"
      >
        <AppIcon name="megaphone" :size="14" />
        {{ store.broadcastPersistenceOnly ? '创建广播指令' : '广播' }}
      </button>
      <button
        v-if="store.canSimulate"
        class="test-btn"
        title="模拟一次烟雾告警"
        @click="emit('simulate')"
      >
        <AppIcon name="flask" :size="14" /> 模拟告警
      </button>
      <button class="bell" title="未处置告警" aria-label="未处置告警" @click="emit('show-alarms')">
        <AppIcon name="bell" :size="20" />
        <span v-show="store.pendingCount > 0" class="bell-badge">
          {{ store.pendingCount > 99 ? '99+' : store.pendingCount }}
        </span>
      </button>

      <div class="user-chip" v-if="store.currentUser">
        <span class="user-avatar" aria-hidden="true"><AppIcon name="user" :size="15" /></span>
        <div class="user-meta">
          <span class="user-name">{{ userName }}</span>
          <span v-if="roleLabel" class="user-role">{{ roleLabel }}</span>
        </div>
        <button class="logout-btn" title="退出登录" aria-label="退出登录" @click="store.logout()">
          <AppIcon name="logout" :size="15" />
        </button>
      </div>

      <div class="conn" :class="{ off: !store.backendConnected }">
        <span class="conn-dot" aria-hidden="true"></span>
        <span>{{ store.backendConnected ? '系统在线 · 实时更新' : '系统连接异常' }}</span>
      </div>
      <div class="clock">{{ clockText }}</div>
      <ThemeToggle />
    </div>
  </header>
</template>
