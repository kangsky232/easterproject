import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ALARM_TYPE, TOKEN_KEY, USER_KEY, type MetricKey } from '@/constants'
import { theme } from '@/theme'
import type {
  Alarm,
  BroadcastRaw,
  ChatResponse,
  Device,
  Notification,
  SystemCapabilities,
  User,
} from '@/api/types'
import { toAlarm, toDevice } from '@/api/mappers'
import { setUnauthorizedHandler } from '@/api/http'
import * as api from '@/api'
import { conc, localTimestamp, toLocalIso } from '@/utils/format'
import { beep } from '@/utils/audio'

function parseStoredUser(): User | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null') as User | null
  } catch (_) {
    return null
  }
}

function isActiveAlarm(alarm: Alarm): boolean {
  return alarm.status === 'pending' || alarm.status === 'confirmed'
}

export type ToastKind = 'success' | 'error' | 'info'

// 趋势时间范围：小时数 → 聚合桶（分钟），保持桶数适中。
function bucketFor(hours: number): number {
  if (hours <= 24) return 15
  if (hours <= 24 * 7) return 60
  return 240
}

// 空序列模板：每个指标一条序列，与烟雾浓度共用同一数据通道。
function emptySeries(): Record<MetricKey, (number | null)[]> {
  return {
    concentration: [],
    temperature: [],
    humidity: [],
    current: [],
    wireTemperature: [],
    coValue: [],
  }
}

// 扩展指标可能为空（旧数据 / 未上报），空值保留为 null 以便折线断点，避免误读为 0。
function toNum(value: number | null | undefined): number | null {
  return value == null ? null : Number(value)
}

export const useDashboardStore = defineStore('dashboard', () => {
  // ---------- 认证 ----------
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const currentUser = ref<User | null>(parseStoredUser())
  const needsLogin = ref(!token.value)
  const loginMessage = ref('')

  // ---------- 数据 ----------
  const backendConnected = ref(false)
  const loading = ref(true)
  const devices = ref<Device[]>([])
  const alarms = ref<Alarm[]>([])
  const notifications = ref<Notification[]>([])
  const broadcasts = ref<BroadcastRaw[]>([])
  const broadcastActionId = ref<number | null>(null)
  const capabilities = ref<SystemCapabilities>({
    mode: 'UNKNOWN',
    storage: 'UNKNOWN',
    deviceIngress: 'UNKNOWN',
    mqtt: 'NOT_CONNECTED',
    visualAi: 'NOT_CONNECTED',
    knowledgeBase: 'UNKNOWN',
    // 能力探测尚未完成时采用保守文案，避免把“已创建”误写成“已送达”。
    broadcast: 'PERSISTENCE_ONLY',
  })
  const overview = ref({
    totalDevices: 0,
    onlineDevices: 0,
    offlineDevices: 0,
    activeAlerts: 0,
  })

  const selectedId = ref<number | null>(null)
  // 0 表示实时模式：展示最近 120 条原始数据，并随全局轮询每 10 秒刷新。
  const trendHours = ref(0)
  const chartTimes = ref<string[]>([])
  const chartSeries = ref<Record<MetricKey, (number | null)[]>>(emptySeries())
  const selectedMetric = ref<MetricKey>('concentration')

  // ---------- 界面提示 ----------
  const toast = ref('')
  const toastVisible = ref(false)
  const toastKind = ref<ToastKind>('info')
  const flashText = ref('')
  const flashVisible = ref(false)

  // 设备接入令牌（一次性展示）
  const tokenModalVisible = ref(false)
  const deviceAccessToken = ref('')

  // 通用确认弹窗
  const confirmVisible = ref(false)
  const confirmTitle = ref('确认操作')
  const confirmMessage = ref('')
  let confirmResolver: ((ok: boolean) => void) | null = null

  let toastTimer: number | undefined
  let flashTimer: number | undefined
  let initializedAlerts = false
  const activeAlertIds = new Set<number>()

  // ---------- 派生状态 ----------
  const selectedDevice = computed(
    () => devices.value.find((device) => device.id === selectedId.value) ?? null,
  )
  const selectedThreshold = computed(() => selectedDevice.value?.threshold ?? 2000)
  // 未处置告警：仅「待处理」状态。
  const pendingCount = computed(() => alarms.value.filter((alarm) => alarm.status === 'pending').length)
  const activeAlertCount = computed(() => alarms.value.filter(isActiveAlarm).length)
  const userRole = computed(() => currentUser.value?.role ?? '')
  const canHandleAlerts = computed(() =>
    ['COMMUNITY_ADMIN', 'SYSTEM_ADMIN', 'FIREFIGHTER'].includes(userRole.value),
  )
  const canBroadcast = computed(() =>
    ['COMMUNITY_ADMIN', 'SYSTEM_ADMIN', 'FIREFIGHTER'].includes(userRole.value),
  )
  const canDeleteBroadcast = computed(() =>
    ['COMMUNITY_ADMIN', 'SYSTEM_ADMIN'].includes(userRole.value),
  )
  const canManageDevices = computed(() =>
    ['COMMUNITY_ADMIN', 'SYSTEM_ADMIN'].includes(userRole.value),
  )
  const canSimulate = computed(
    () => canManageDevices.value && capabilities.value.mode === 'LOCAL_DEVELOPMENT',
  )
  const broadcastPersistenceOnly = computed(
    () => capabilities.value.broadcast === 'PERSISTENCE_ONLY',
  )

  const kpiItems = computed(() => [
    { key: 'total_devices', label: '设备总数', color: theme.accent, value: overview.value.totalDevices },
    { key: 'online', label: '在线', color: theme.good, value: overview.value.onlineDevices },
    { key: 'offline', label: '离线', color: theme.serious, value: overview.value.offlineDevices },
    { key: 'alarm', label: '告警中', color: theme.critical, value: overview.value.activeAlerts },
    { key: 'unhandled_alarms', label: '未处置告警', color: theme.warning, value: pendingCount.value },
  ])

  // ---------- 会话 ----------
  function setToken(value: string): void {
    token.value = value
    if (value) localStorage.setItem(TOKEN_KEY, value)
    else localStorage.removeItem(TOKEN_KEY)
  }

  function handleUnauthorized(): void {
    setToken('')
    currentUser.value = null
    localStorage.removeItem(USER_KEY)
    needsLogin.value = true
    loginMessage.value = '登录已失效，请重新登录。'
  }

  function requireLogin(message = ''): void {
    loginMessage.value = message
    needsLogin.value = true
  }

  async function login(username: string, password: string): Promise<void> {
    loginMessage.value = '正在登录…'
    try {
      const data = await api.login(username, password)
      setToken(data.token)
      currentUser.value = data.user
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      needsLogin.value = false
      loginMessage.value = ''
      await refreshAll()
    } catch (error) {
      loginMessage.value = `登录失败：${(error as Error).message}`
    }
  }

  function logout(): void {
    setToken('')
    currentUser.value = null
    localStorage.removeItem(USER_KEY)
    devices.value = []
    alarms.value = []
    notifications.value = []
    broadcasts.value = []
    overview.value = { totalDevices: 0, onlineDevices: 0, offlineDevices: 0, activeAlerts: 0 }
    selectedId.value = null
    chartTimes.value = []
    chartSeries.value = emptySeries()
    selectedMetric.value = 'concentration'
    needsLogin.value = true
    loginMessage.value = ''
  }

  // ---------- 数据拉取 ----------
  async function checkBackend(): Promise<void> {
    try {
      await api.checkHealth()
      backendConnected.value = true
    } catch (_) {
      backendConnected.value = false
    }
  }

  async function fetchCapabilities(): Promise<void> {
    capabilities.value = await api.fetchCapabilities()
  }

  async function fetchStats(): Promise<void> {
    const data = await api.fetchOverview()
    overview.value = {
      totalDevices: data.totalDevices ?? 0,
      onlineDevices: data.onlineDevices ?? 0,
      offlineDevices: data.offlineDevices ?? 0,
      activeAlerts: data.activeAlerts ?? 0,
    }
  }

  async function fetchDevices(): Promise<void> {
    const page = await api.fetchDevices()
    devices.value = (page.records || []).map(toDevice)
    if (selectedId.value !== null && !devices.value.some((device) => device.id === selectedId.value)) {
      selectedId.value = null
    }
    if (selectedId.value === null && devices.value.length > 0) {
      selectedId.value = devices.value[0].id
    }
    syncDeviceStatuses()
  }

  async function fetchAlarms(): Promise<void> {
    const page = await api.fetchAlerts()
    alarms.value = (page.records || []).map(toAlarm)
    notifyNewAlerts()
    syncDeviceStatuses()
  }

  async function fetchTrend(deviceId: number): Promise<void> {
    try {
      if (trendHours.value === 0) {
        const records = (await api.fetchHistory(deviceId)).slice().reverse()
        chartTimes.value = records.map((item) => item.timestamp)
        chartSeries.value = {
          concentration: records.map((item) => Number(item.concentration ?? 0)),
          temperature: records.map((item) => toNum(item.temperature)),
          humidity: records.map((item) => toNum(item.humidity)),
          current: records.map((item) => toNum(item.currentValue)),
          wireTemperature: records.map((item) => toNum(item.wireTemperature)),
          coValue: records.map((item) => toNum(item.coValue)),
        }
        return
      }

      const end = new Date()
      const start = new Date(end.getTime() - trendHours.value * 3_600_000)
      const records = await api.fetchTrend(deviceId, {
        start: toLocalIso(start),
        end: toLocalIso(end),
        bucketMinutes: bucketFor(trendHours.value),
      })
      chartTimes.value = records.map((item) => item.bucketStart)
      chartSeries.value = {
        concentration: records.map((item) => Number(item.average ?? 0)),
        temperature: records.map((item) => toNum(item.averageTemperature)),
        humidity: records.map((item) => toNum(item.averageHumidity)),
        current: records.map((item) => toNum(item.averageCurrent)),
        wireTemperature: records.map((item) => toNum(item.averageWireTemperature)),
        coValue: records.map((item) => toNum(item.averageCoValue)),
      }
    } catch (error) {
      console.warn('无法加载设备趋势：', (error as Error).message)
    }
  }

  async function fetchNotifications(): Promise<void> {
    const page = await api.fetchNotifications()
    notifications.value = page.records || []
  }

  async function fetchBroadcasts(): Promise<void> {
    const page = await api.fetchBroadcasts()
    broadcasts.value = page.records || []
  }

  async function refreshAll(): Promise<void> {
    const systemResults = await Promise.allSettled([checkBackend(), fetchCapabilities()])
    for (const result of systemResults) {
      if (result.status === 'rejected') console.warn('系统状态刷新失败：', result.reason)
    }
    if (!token.value) {
      loading.value = false
      return
    }
    const results = await Promise.allSettled([
      fetchStats(),
      fetchDevices(),
      fetchAlarms(),
      fetchNotifications(),
      fetchBroadcasts(),
    ])
    for (const result of results) {
      if (result.status === 'rejected') console.warn('刷新数据失败：', result.reason)
    }
    if (selectedId.value !== null) {
      await fetchTrend(selectedId.value)
    }
    loading.value = false
  }

  function syncDeviceStatuses(): void {
    const activeSmoke = new Set(
      alarms.value.filter(isActiveAlarm).filter((alarm) => alarm.alarmType === 'SMOKE').map((alarm) => alarm.deviceCode),
    )
    const activeOffline = new Set(
      alarms.value.filter(isActiveAlarm).filter((alarm) => alarm.alarmType === 'OFFLINE').map((alarm) => alarm.deviceCode),
    )
    devices.value.forEach((device) => {
      device.status = activeSmoke.has(device.deviceCode)
        ? 'alarm'
        : activeOffline.has(device.deviceCode)
          ? 'offline'
          : device.online
            ? 'online'
            : 'offline'
    })
  }

  function notifyNewAlerts(): void {
    const current = new Set(alarms.value.filter(isActiveAlarm).map((alarm) => alarm.id))
    if (initializedAlerts) {
      const newAlert = alarms.value.find(
        (alarm) => current.has(alarm.id) && !activeAlertIds.has(alarm.id),
      )
      if (newAlert) {
        const type = ALARM_TYPE[newAlert.alarmType]?.label ?? '未知'
        const detail = newAlert.alarmType === 'SMOKE' ? `浓度 ${conc(newAlert.currentValue)} ppm` : '设备离线'
        const text = `设备 ${newAlert.deviceCode} 触发${type}告警 · ${detail}`
        showToast(`⚠️ 新告警：${text}`, 'error')
        triggerFlash(text)
        beep()
      }
    }
    initializedAlerts = true
    activeAlertIds.clear()
    current.forEach((id) => activeAlertIds.add(id))
  }

  // ---------- 交互 ----------
  function selectDevice(id: number): void {
    selectedId.value = id
    void fetchTrend(id)
  }

  function setTrendHours(hours: number): void {
    if (trendHours.value === hours) return
    trendHours.value = hours
    if (selectedId.value !== null) void fetchTrend(selectedId.value)
  }

  function selectMetric(key: MetricKey): void {
    if (selectedMetric.value === key) return
    selectedMetric.value = key
  }

  function showToast(text: string, kind: ToastKind = 'info'): void {
    toast.value = text
    toastKind.value = kind
    toastVisible.value = true
    window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => {
      toastVisible.value = false
    }, 6000)
  }

  function triggerFlash(text: string): void {
    flashText.value = text
    flashVisible.value = true
    window.clearTimeout(flashTimer)
    flashTimer = window.setTimeout(() => {
      flashVisible.value = false
    }, 5000)
  }

  function hideFlash(): void {
    flashVisible.value = false
  }

  function showDeviceToken(value: string): void {
    deviceAccessToken.value = value
    tokenModalVisible.value = true
  }

  function closeTokenModal(): void {
    tokenModalVisible.value = false
    deviceAccessToken.value = ''
  }

  function confirm(message: string, title = '确认操作'): Promise<boolean> {
    confirmTitle.value = title
    confirmMessage.value = message
    confirmVisible.value = true
    return new Promise((resolve) => {
      confirmResolver = resolve
    })
  }

  function resolveConfirm(ok: boolean): void {
    confirmVisible.value = false
    confirmResolver?.(ok)
    confirmResolver = null
  }

  async function simulateAlarm(): Promise<void> {
    if (!canSimulate.value) {
      showToast('当前账号或运行模式不允许模拟告警。', 'error')
      return
    }
    const activeDevices = new Set(alarms.value.filter(isActiveAlarm).map((alarm) => alarm.deviceCode))
    const device =
      devices.value.find((item) => item.id === selectedId.value && !activeDevices.has(item.deviceCode)) ||
      devices.value.find((item) => !activeDevices.has(item.deviceCode))
    if (!device) {
      showToast('所有设备均有未处置告警，请先完成处置后再模拟。', 'error')
      return
    }
    const concentration = Math.round(Number(device.threshold || 2000) + 30)
    try {
      await api.reportTelemetry({
        deviceId: device.deviceCode,
        concentration,
        messageId: `dashboard-demo-${Date.now()}`,
        timestamp: localTimestamp(),
      })
      selectDevice(device.id)
      await refreshAll()
    } catch (error) {
      showToast(`模拟告警失败：${(error as Error).message}`, 'error')
    }
  }

  async function handleAlarm(id: number, action: string): Promise<void> {
    if (!canHandleAlerts.value) {
      showToast('当前账号没有告警处置权限。', 'error')
      return
    }
    try {
      await api.handleAlert(id, action)
      await refreshAll()
    } catch (error) {
      showToast(`告警处置失败：${(error as Error).message}`, 'error')
    }
  }

  async function verifyAlarm(id: number): Promise<void> {
    if (!canHandleAlerts.value) {
      showToast('当前账号没有告警复核权限。', 'error')
      return
    }
    try {
      const review = await api.verifyAlert(id)
      const alarm = alarms.value.find((item) => item.id === id)
      if (alarm) alarm.verifyResult = review.reviewResult
      showToast(`🔎 ${review.reviewResult}`, 'success')
    } catch (error) {
      showToast(`告警复核失败：${(error as Error).message}`, 'error')
    }
  }

  interface SaveDevicePayload {
    id?: number
    deviceId: string
    deviceName: string
    location: string
    threshold: number
  }

  async function saveDevice(payload: SaveDevicePayload): Promise<boolean> {
    if (!canManageDevices.value) {
      showToast('当前账号没有设备管理权限。', 'error')
      return false
    }
    if (!payload.deviceId || !payload.deviceName || !payload.location) {
      showToast('请完整填写设备编码、名称和安装位置。', 'error')
      return false
    }
    if (!Number.isInteger(payload.threshold) || payload.threshold < 1) {
      showToast('阈值必须是大于 0 的整数 ppm。', 'error')
      return false
    }
    try {
      let targetId = Number(payload.id) || 0
      if (payload.id) {
        await api.updateDevice(targetId, {
          deviceName: payload.deviceName,
          location: payload.location,
        })
      } else {
        const device = await api.bindDevice({
          deviceId: payload.deviceId,
          deviceName: payload.deviceName,
          location: payload.location,
        })
        targetId = device.id
        if (device.deviceAccessToken) showDeviceToken(device.deviceAccessToken)
      }
      await api.updateThreshold(targetId, payload.threshold)
      await refreshAll()
      return true
    } catch (error) {
      showToast(`保存失败：${(error as Error).message}`, 'error')
      return false
    }
  }

  async function deleteDevice(id: number): Promise<void> {
    if (!canManageDevices.value) {
      showToast('当前账号没有设备管理权限。', 'error')
      return
    }
    const device = devices.value.find((item) => item.id === id)
    const ok = await confirm(`确定解绑设备“${device?.deviceCode ?? id}”吗？历史监测数据会保留。`, '解绑设备')
    if (!ok) return
    try {
      await api.deleteDevice(id)
      if (selectedId.value === id) {
        selectedId.value = null
        chartTimes.value = []
        chartSeries.value = emptySeries()
      }
      await refreshAll()
    } catch (error) {
      showToast(`解绑失败：${(error as Error).message}`, 'error')
    }
  }

  function currentAlarmId(): number | null {
    const device = selectedDevice.value
    const active = alarms.value.find(
      (alarm) => alarm.deviceCode === device?.deviceCode && isActiveAlarm(alarm),
    )
    return active?.id ?? null
  }

  async function sendBroadcast(content: string): Promise<boolean> {
    if (!canBroadcast.value) {
      showToast('当前账号没有创建广播指令的权限。', 'error')
      return false
    }
    const device = selectedDevice.value
    if (!device) {
      showToast('请先在监控大屏中选择要接收广播的设备。', 'error')
      return false
    }
    const trimmed = content.trim()
    if (!trimmed) return false
    try {
      await api.createBroadcast({
        deviceId: device.deviceCode,
        content: trimmed,
        triggerAlertId: currentAlarmId(),
      })
      const deliveryNote = broadcastPersistenceOnly.value ? '（当前仅保存记录，尚未下发）' : ''
      showToast(`📣 广播指令已创建${deliveryNote}，目标设备：${device.deviceCode}`, 'success')
      await fetchBroadcasts()
      return true
    } catch (error) {
      showToast(`广播指令创建失败：${(error as Error).message}`, 'error')
      return false
    }
  }

  async function deliverBroadcast(id: number): Promise<void> {
    if (!canBroadcast.value) {
      showToast('当前账号没有广播下发权限。', 'error')
      return
    }
    if (broadcastPersistenceOnly.value) {
      showToast('钉钉广播尚未配置，当前记录无法下发。', 'error')
      return
    }
    const broadcast = broadcasts.value.find((item) => item.id === id)
    const ok = await confirm(
      `确定再次下发广播 #${id} 到钉钉吗？\n\n${broadcast?.content ?? ''}`,
      '下发广播',
    )
    if (!ok) return
    broadcastActionId.value = id
    try {
      const result = await api.deliverBroadcast(id)
      await fetchBroadcasts()
      if (result.status === 1) {
        showToast(`📣 广播 #${id} 已成功下发到钉钉。`, 'success')
      } else {
        showToast(`广播 #${id} 下发失败，请检查钉钉连接和接收人绑定。`, 'error')
      }
    } catch (error) {
      showToast(`广播下发失败：${(error as Error).message}`, 'error')
    } finally {
      broadcastActionId.value = null
    }
  }

  async function deleteBroadcast(id: number): Promise<void> {
    if (!canDeleteBroadcast.value) {
      showToast('当前账号没有删除广播记录的权限。', 'error')
      return
    }
    const ok = await confirm(
      `确定删除广播记录 #${id} 吗？此操作不会撤回钉钉中已经送达的消息。`,
      '删除广播记录',
    )
    if (!ok) return
    broadcastActionId.value = id
    try {
      await api.deleteBroadcast(id)
      broadcasts.value = broadcasts.value.filter((item) => item.id !== id)
      showToast(`广播记录 #${id} 已删除。`, 'success')
    } catch (error) {
      showToast(`删除广播记录失败：${(error as Error).message}`, 'error')
    } finally {
      broadcastActionId.value = null
    }
  }

  async function sendChat(question: string): Promise<ChatResponse | null> {
    const trimmed = question.trim()
    if (!trimmed) return null
    try {
      return await api.chat(trimmed, currentAlarmId())
    } catch (error) {
      const answer = `智能问答请求失败：${(error as Error).message}`
      return { answer, summary: answer, source: 'CLIENT_ERROR', riskLevel: 'UNKNOWN' }
    }
  }

  // 401 时由 http 层回调，弹出登录框。
  setUnauthorizedHandler(handleUnauthorized)

  return {
    // 认证
    token,
    currentUser,
    needsLogin,
    loginMessage,
    // 数据
    backendConnected,
    loading,
    devices,
    alarms,
    notifications,
    broadcasts,
    broadcastActionId,
    capabilities,
    overview,
    selectedId,
    trendHours,
    chartTimes,
    chartSeries,
    selectedMetric,
    // 界面提示
    toast,
    toastVisible,
    toastKind,
    flashText,
    flashVisible,
    tokenModalVisible,
    deviceAccessToken,
    confirmVisible,
    confirmTitle,
    confirmMessage,
    // 派生
    selectedDevice,
    selectedThreshold,
    pendingCount,
    activeAlertCount,
    userRole,
    canHandleAlerts,
    canBroadcast,
    canDeleteBroadcast,
    canManageDevices,
    canSimulate,
    broadcastPersistenceOnly,
    kpiItems,
    // 方法
    requireLogin,
    login,
    logout,
    refreshAll,
    selectDevice,
    setTrendHours,
    selectMetric,
    simulateAlarm,
    handleAlarm,
    verifyAlarm,
    saveDevice,
    deleteDevice,
    sendBroadcast,
    deliverBroadcast,
    deleteBroadcast,
    sendChat,
    showToast,
    hideFlash,
    showDeviceToken,
    closeTokenModal,
    confirm,
    resolveConfirm,
  }
})
