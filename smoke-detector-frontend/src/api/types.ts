import type {
  AlarmSeverity,
  AlarmStatus,
  AlarmType,
  DeviceStatus,
  ModuleKey,
  PermissionCode,
} from '@/constants'

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
  id: number
  alertId: number
  channel: string
  receiver?: string
  deviceId?: string
  content?: string
  status?: string
  sentAt?: string | null
  auditStatus: NotificationAuditStatus
  auditResult?: NotificationAuditResult | null
  auditorUsername?: string | null
  auditRemark?: string | null
  auditedAt?: string | null
  createdAt?: string | null
}

export type NotificationAuditStatus = 'PENDING' | 'COMPLETED'
export type NotificationAuditResult = 'NORMAL' | 'FOLLOWED_UP'

export interface NotificationSummary {
  total: number
  appCount: number
  smsCount: number
  dingTalkCount: number
  pendingCount: number
  sentCount: number
  failedCount: number
  pendingAuditCount: number
  completedAuditCount: number
  attentionCount: number
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

export interface UserAccount {
  id: number
  username: string
  displayName: string
  role: 'RESIDENT' | 'COMMUNITY_ADMIN' | 'SYSTEM_ADMIN' | 'FIREFIGHTER'
  enabled: boolean
  phone?: string | null
  createdAt?: string | null
}

export interface LoginResponse {
  token: string
  user: User
}

export interface RoleWorkspace {
  roleCode: string
  roleLabel: string
  homeTitle: string
  description: string
  modules: ModuleKey[]
  permissions: PermissionCode[]
}

export interface MapBuilding {
  buildingCode: string
  buildingName: string
  positionX: number
  positionZ: number
  width: number
  depth: number
  floors: number
}

export interface MapDevice {
  id: number
  deviceId: string
  deviceName?: string
  location?: string
  buildingCode?: string | null
  buildingName?: string | null
  floorNo?: number | null
  roomLabel?: string | null
  positionX?: number | null
  positionZ?: number | null
  online: boolean
  status: 'ONLINE' | 'OFFLINE' | 'ALARM'
  alertSeverity?: 'WARNING' | 'DANGER' | 'OFFLINE' | null
  battery?: number | null
  smoke?: number | null
  temperature?: number | null
  humidity?: number | null
  current?: number | null
  wireTemperature?: number | null
  coValue?: number | null
  latestTimestamp?: string | null
}

export interface MapScene {
  sceneCode: string
  sceneName: string
  width: number
  depth: number
  buildings: MapBuilding[]
  devices: MapDevice[]
}

export interface MapPositionPayload {
  buildingCode: string
  floorNo: number
  roomLabel: string
  positionX: number
  positionZ: number
}

export type HazardStatus = 'REPORTED' | 'PROCESSING' | 'PENDING_REVIEW' | 'CLOSED'
export type HazardPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type HazardActionType = 'REPORTED' | 'CLAIMED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface HazardTicket {
  id: number
  ticketNo: string
  title: string
  description: string
  location: string
  priority: HazardPriority
  status: HazardStatus
  reporterUsername: string
  assigneeUsername?: string | null
  resolution?: string | null
  reviewerUsername?: string | null
  createdAt: string
  updatedAt: string
  closedAt?: string | null
}

export interface HazardAction {
  id: number
  ticketId: number
  actionType: HazardActionType
  operatorName: string
  remark: string
  createdAt: string
}

export interface HazardDetail {
  ticket: HazardTicket
  actions: HazardAction[]
}

export interface HazardSummary {
  reported: number
  processing: number
  pendingReview: number
  closed: number
  openTotal: number
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

export type Notification = NotificationRaw
