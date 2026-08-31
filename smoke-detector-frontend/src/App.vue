<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { MAX_REFRESH_MS, REFRESH_MS, type ModuleKey } from '@/constants'
import { useDashboardStore } from '@/store/dashboard'
import { resumeAudio } from '@/utils/audio'
import TopBar from '@/components/TopBar.vue'
import DeviceList from '@/components/DeviceList.vue'
import TrendChart from '@/components/TrendChart.vue'
import AlarmList from '@/components/AlarmList.vue'
import KpiRow from '@/components/KpiRow.vue'
import AlertBar from '@/components/AlertBar.vue'
import DeviceManageView from '@/components/DeviceManageView.vue'
import ChatView from '@/components/ChatView.vue'
import NotificationsView from '@/components/NotificationsView.vue'
import BroadcastsView from '@/components/BroadcastsView.vue'
import BroadcastModal from '@/components/BroadcastModal.vue'
import BroadcastToast from '@/components/BroadcastToast.vue'
import FlashOverlay from '@/components/FlashOverlay.vue'
import TokenModal from '@/components/TokenModal.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import LoginModal from '@/components/LoginModal.vue'
import Map3DView from '@/components/Map3DView.vue'
import RoleWorkspaceBanner from '@/components/RoleWorkspaceBanner.vue'
import UserAdminEntryView from '@/components/UserAdminEntryView.vue'
import HomeView from '@/components/HomeView.vue'
import TrendView from '@/components/TrendView.vue'

const store = useDashboardStore()

type ViewName =
  | 'home'
  | 'trend'
  | 'monitor'
  | 'map'
  | 'devices'
  | 'chat'
  | 'notifications'
  | 'broadcasts'
  | 'users'

const FUNCTION_VIEWS: ViewName[] = [
  'monitor',
  'map',
  'devices',
  'chat',
  'notifications',
  'broadcasts',
  'users',
]

const view = ref<ViewName>('home')
const isFeature = computed(() => FUNCTION_VIEWS.includes(view.value))

const tabs = computed(() => {
  const role = store.userRole
  const labels: Record<string, Partial<Record<ModuleKey, string>>> = {
    RESIDENT: { monitor: '🏠 我的安全', map: '🏙️ 社区 3D', chat: '💬 安全问答' },
    FIREFIGHTER: { monitor: '🚨 应急总览', map: '🏙️ 3D 态势', chat: '💬 智能辅助', notifications: '🔔 告警通知', broadcasts: '📣 应急广播' },
    COMMUNITY_ADMIN: { monitor: '📊 小区监控', map: '🏙️ 3D 社区', devices: '🔧 设备管理', chat: '💬 智能问答', notifications: '🔔 通知记录', broadcasts: '📣 广播管理' },
    SYSTEM_ADMIN: { monitor: '📊 系统总览', map: '🏙️ 3D 社区', devices: '🔧 设备管理', chat: '💬 智能问答', notifications: '🔔 通知审计', broadcasts: '📣 广播管理', users: '👥 用户管理' },
  }
  const fallback: Record<ModuleKey, string> = { monitor: '📊 监控大屏', map: '🏙️ 模拟 3D 地图', devices: '🔧 设备管理', chat: '💬 智能问答', notifications: '🔔 通知记录', broadcasts: '📣 广播记录', users: '👥 用户管理' }
  const order: ModuleKey[] = ['monitor', 'map', 'devices', 'chat', 'notifications', 'broadcasts', 'users']
  return order
    .filter((key) => store.canViewModule(key))
    .map((key) => ({ key, label: labels[role]?.[key] ?? fallback[key] }))
})

const showBroadcast = ref(false)

function navigate(name: string): void {
  view.value = name as ViewName
  if (name === 'devices' || name === 'notifications' || name === 'broadcasts') {
    void store.refreshAll()
  }
}

function goHome(): void {
  view.value = 'home'
}

function openLogin(): void {
  store.requireLogin()
}

function openBroadcast(): void {
  if (store.canBroadcast) showBroadcast.value = true
}

async function scrollToAlarms(): Promise<void> {
  view.value = 'monitor'
  await nextTick()
  document.getElementById('panel-alarms')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onSimulate(): void {
  void store.simulateAlarm()
}

watch(tabs, (available) => {
  if (!isFeature.value) return
  if (!available.some((tab) => tab.key === view.value)) view.value = 'monitor'
})

// 轮询 + 断线退避：后端在线按固定周期刷新，离线时指数退避，恢复后复位。
let pollTimer: number | undefined
let pollDelay = REFRESH_MS

async function poll(): Promise<void> {
  await store.refreshAll()
  pollDelay = store.backendConnected ? REFRESH_MS : Math.min(pollDelay * 2, MAX_REFRESH_MS)
  pollTimer = window.setTimeout(poll, pollDelay)
}

onMounted(() => {
  void poll()
  // 任意用户交互时恢复被浏览器挂起的音频上下文。
  document.addEventListener('pointerdown', resumeAudio)
})

onUnmounted(() => {
  window.clearTimeout(pollTimer)
  document.removeEventListener('pointerdown', resumeAudio)
})
</script>

<template>
  <Transition name="view" mode="out-in">
    <HomeView v-if="view === 'home'" key="home" @navigate="navigate" @open-login="openLogin" />

    <TrendView v-else-if="view === 'trend'" key="trend" @back="goHome" />

    <div v-else key="feature" class="feature-view">
      <button type="button" class="back-home" @click="goHome">← 返回首页</button>
      <TopBar @broadcast="openBroadcast" @simulate="onSimulate" @show-alarms="scrollToAlarms" @home="goHome" />

      <nav class="tabs" role="tablist" aria-label="功能导航">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          role="tab"
          :aria-selected="view === tab.key"
          :class="{ active: view === tab.key }"
          @click="navigate(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <AlertBar @show-alarms="scrollToAlarms" />

      <div v-if="!store.backendConnected && !store.loading" class="conn-banner" role="alert">
        <span>后端连接中断，正在自动重试…</span>
        <button class="btn-mini" type="button" @click="store.refreshAll()">立即重试</button>
      </div>

      <div v-show="view === 'monitor'" class="view-pane monitor-pane">
        <RoleWorkspaceBanner />
        <template v-if="store.loading">
          <div class="kpi-row">
            <div v-for="i in 5" :key="i" class="skeleton skeleton-kpi"></div>
          </div>
          <main class="grid monitor-grid">
            <div class="skeleton skeleton-panel"></div>
            <div class="skeleton skeleton-panel skeleton-panel--wide"></div>
            <div class="skeleton skeleton-panel"></div>
          </main>
        </template>
        <template v-else>
          <KpiRow />
          <main class="grid monitor-grid">
            <DeviceList />
            <div class="grid-center">
              <TrendChart />
            </div>
            <AlarmList />
          </main>
        </template>
      </div>

      <div v-show="view === 'map'" class="view-pane">
        <Map3DView />
      </div>

      <div v-show="view === 'devices'" class="view-pane">
        <DeviceManageView />
      </div>

      <div v-show="view === 'chat'" class="view-pane">
        <ChatView />
      </div>

      <div v-show="view === 'notifications'" class="view-pane">
        <NotificationsView />
      </div>

      <div v-show="view === 'broadcasts'" class="view-pane">
        <BroadcastsView />
      </div>

      <div v-show="view === 'users'" class="view-pane">
        <UserAdminEntryView />
      </div>
    </div>
  </Transition>

  <BroadcastModal :open="showBroadcast" @close="showBroadcast = false" />
  <BroadcastToast />
  <FlashOverlay />
  <TokenModal />
  <ConfirmModal />
  <LoginModal />
</template>
