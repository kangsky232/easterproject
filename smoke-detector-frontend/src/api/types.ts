import type { AlarmSeverity, AlarmStatus, AlarmType, DeviceStatus } from '@/constants'

// ---------- 后端原始响应（camelCase） ----------

export interface PageResult<T> {
  records: T[]
  total?: number
  page?: number
  pageSize?: number
}

export interface DeviceRaw {
  id: number
  deviceId: string
  deviceName?: string
  location?: string
  threshold: number
  battery?: number | null
  latestConcentration?: number | null
  latestTemperature?: number | null
  latestHumidity?: number | null
  latestCurrent?: number | null
  latestWireTemperature?: number | null
  latestCoValue?: number | null
  latestBeepStatus?: string | null
  latestTimestamp?: string | null
  online: boolean
  deviceAccessToken?: string
}

export interface AlarmRaw {
  id: number
  deviceId: string
  alertType: number
  concentration?: number | null
  threshold?: number | null
  severity?: AlarmSeverity | null
  ruleDescription?: string | null
  status: number
  falseAlarm?: number | boolean
  createdAt?: string | null
}

export interface HistoryPointRaw {
  timestamp: string
  concentration: number
  temperature?: number | null
  humidity?: number | null
  currentValue?: number | null
  wireTemperature?: number | null
  coValue?: number | null
  beepStatus?: string | null
}

export interface TrendPointRaw {
  bucketStart: string
  average: number
  minimum: number
  maximum: number
  samples: number
  averageTemperature?: number | null
  averageHumidity?: number | null
  averageCurrent?: number | null
  averageWireTemperature?: number | null
  averageCoValue?: number | null
}

export interface NotificationRaw {
  id?: number
  channel: string
  receiver?: string
  deviceId?: string
  content?: string
  status?: string
  sentAt?: string | null
  createdAt?: string | null
}

export interface BroadcastRaw {
  id: number
  deviceId: string
  content: string
  triggerAlertId?: number | null
  status: number
  executedAt?: string | null
  createdAt?: string | null
}

export interface SystemCapabilities {
  mode: string
  storage: string
  deviceIngress: string
  mqtt: string
  visualAi: string
  knowledgeBase: string
  llmProvider?: string
  llmModel?: string
  broadcast: string
}

export interface OverviewRaw {
  totalDevices?: number
  onlineDevices?: number
  offlineDevices?: number
  activeAlerts?: number
}

export interface User {
  id?: number
  username?: string
  displayName?: string
  role?: string
  [key: string]: unknown
}

export interface LoginResponse {
  token: string
  user: User
}

export interface ReviewResponse {
  reviewResult: string
}

export interface ChatResponse {
  answer: string
  source: string
  model?: string
  riskLevel?: 'UNKNOWN' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  summary?: string
  immediateActions?: string[]
  verificationSteps?: string[]
  escalationConditions?: string[]
  safetyNotice?: string
  sources?: Array<{ id: string; title: string }>
}

export interface BindResponse {
  id: number
  deviceAccessToken?: string
}

// ---------- 视图模型 ----------

export interface Device {
  id: number
  deviceCode: string
  name: string
  location: string
  threshold: number
  battery: number | null
  latestConcentration: number | null
  latestTemperature: number | null
  latestHumidity: number | null
  latestCurrent: number | null
  latestWireTemperature: number | null
  latestCoValue: number | null
  latestBeepStatus: string | null
  latestTime: string | null
  online: boolean
  status: DeviceStatus
}

export interface Alarm {
  id: number
  deviceCode: string
  alarmType: AlarmType
  currentValue: number | null
  thresholdValue: number | null
  severity: AlarmSeverity | null
  ruleDescription: string | null
  status: AlarmStatus
  createdAt: string | null
  verifyResult?: string
}

export interface Notification {
  channel: string
  receiver?: string
  deviceId?: string
  content?: string
  status?: string
  sentAt?: string | null
  createdAt?: string | null
}
