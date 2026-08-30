<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { MAX_REFRESH_MS, MODULE_LABELS, REFRESH_MS, type ModuleKey } from '@/constants'
import { useDashboardStore } from '@/store/dashboard'
import { resumeAudio } from '@/utils/audio'
import TopBar from '@/components/TopBar.vue'
import KpiRow from '@/components/KpiRow.vue'
import DeviceList from '@/components/DeviceList.vue'
import TrendChart from '@/components/TrendChart.vue'
import AlarmList from '@/components/AlarmList.vue'
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
import UserAdminEntryView from '@/components/UserAdminEntryView.vue'

const store = useDashboardStore()

const tabs = computed(() =>
  store.visibleModules.map((key) => ({ key, label: MODULE_LABELS[key] })),
)

const activeTab = ref<ModuleKey>('monitor')
const showBroadcast = ref(false)

function switchTab(name: ModuleKey): void {
  if (!store.canViewModule(name)) return
  activeTab.value = name
  if (name === 'devices' || name === 'notifications' || name === 'broadcasts') {
    void store.refreshAll()
  }
}

function openBroadcast(): void {
  if (store.canBroadcast) showBroadcast.value = true
}

async function scrollToAlarms(): Promise<void> {
  activeTab.value = 'monitor'
  await nextTick()
  document.getElementById('panel-alarms')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onSimulate(): void {
  void store.simulateAlarm()
}

watch(tabs, (available) => {
  if (!available.some((tab) => tab.key === activeTab.value)) activeTab.value = 'monitor'
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
  <TopBar @broadcast="openBroadcast" @simulate="onSimulate" @show-alarms="scrollToAlarms" />

  <nav v-if="store.token" class="tabs" role="tablist" aria-label="功能导航">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      class="tab"
      role="tab"
      :aria-selected="activeTab === tab.key"
      :class="{ active: activeTab === tab.key }"
      @click="switchTab(tab.key)"
    >
      {{ tab.label }}
    </button>
  </nav>

  <AlertBar v-if="store.token" @show-alarms="scrollToAlarms" />

  <div v-if="!store.backendConnected && !store.loading" class="conn-banner" role="alert">
    <span>系统连接异常，正在自动重试…</span>
    <button class="btn-mini" type="button" @click="store.refreshAll()">立即重试</button>
  </div>

  <div v-if="store.canViewModule('monitor')" v-show="activeTab === 'monitor'">
    <template v-if="store.loading">
      <div class="kpi-row">
        <div v-for="i in 5" :key="i" class="skeleton skeleton-kpi"></div>
      </div>
      <main class="grid">
        <div class="skeleton skeleton-panel"></div>
        <div class="skeleton skeleton-panel skeleton-panel--wide"></div>
        <div class="skeleton skeleton-panel"></div>
      </main>
    </template>
    <template v-else>
      <KpiRow />
      <main class="grid">
        <DeviceList />
        <TrendChart />
        <AlarmList />
      </main>
    </template>
  </div>

  <div v-if="store.canViewModule('map')" v-show="activeTab === 'map'">
    <Map3DView />
  </div>

  <div v-if="store.canViewModule('devices')" v-show="activeTab === 'devices'">
    <DeviceManageView />
  </div>

  <div v-if="store.canViewModule('chat')" v-show="activeTab === 'chat'">
    <ChatView />
  </div>

  <div v-if="store.canViewModule('notifications')" v-show="activeTab === 'notifications'">
    <NotificationsView />
  </div>

  <div v-if="store.canViewModule('broadcasts')" v-show="activeTab === 'broadcasts'">
    <BroadcastsView />
  </div>

  <div v-if="store.canViewModule('users')" v-show="activeTab === 'users'">
    <UserAdminEntryView />
  </div>

  <BroadcastModal :open="showBroadcast" @close="showBroadcast = false" />
  <BroadcastToast />
  <FlashOverlay />
  <TokenModal />
  <ConfirmModal />
  <LoginModal />
</template>
