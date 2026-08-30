import { MAX_CHART_POINTS } from '@/constants'
import { api } from './http'
import type {
  AlarmRaw,
  BindResponse,
  BroadcastRaw,
  ChatResponse,
  DeviceRaw,
  HistoryPointRaw,
  LoginResponse,
  MapPositionPayload,
  MapScene,
  NotificationRaw,
  OverviewRaw,
  PageResult,
  ReviewResponse,
  RoleWorkspace,
  SystemCapabilities,
  TrendPointRaw,
  UserAccount,
} from './types'

export function checkHealth(): Promise<unknown> {
  return api('/api/health')
}

export function fetchCapabilities(): Promise<SystemCapabilities> {
  return api('/api/system/capabilities')
}

export function fetchWorkspace(): Promise<RoleWorkspace> {
  return api('/api/auth/workspace')
}

export function fetchMapScene(): Promise<MapScene> {
  return api('/api/map/scene')
}

export function updateMapPosition(id: number, payload: MapPositionPayload): Promise<unknown> {
  return api(`/api/map/devices/${id}/position`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function fetchOverview(): Promise<OverviewRaw> {
  return api('/api/dashboard/overview')
}

export function fetchDevices(): Promise<PageResult<DeviceRaw>> {
  return api('/api/devices?page=1&pageSize=200')
}

export function fetchAlerts(): Promise<PageResult<AlarmRaw>> {
  return api('/api/alerts?page=1&pageSize=200')
}

export function fetchHistory(deviceId: number): Promise<HistoryPointRaw[]> {
  return api(`/api/devices/${deviceId}/history?limit=${MAX_CHART_POINTS}`)
}

export interface TrendQuery {
  start: string
  end: string
  bucketMinutes: number
}

export function fetchTrend(deviceId: number, query: TrendQuery): Promise<TrendPointRaw[]> {
  const { start, end, bucketMinutes } = query
  return api(
    `/api/devices/${deviceId}/trend?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}&bucketMinutes=${bucketMinutes}`,
  )
}

export function fetchNotifications(): Promise<PageResult<NotificationRaw>> {
  return api('/api/notifications?page=1&pageSize=100')
}

export function fetchBroadcasts(): Promise<PageResult<BroadcastRaw>> {
  return api('/api/broadcasts?page=1&pageSize=100')
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return api('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export interface TelemetryPayload {
  deviceId: string
  concentration: number
  temperature?: number
  humidity?: number
  current?: number
  wireTemperature?: number
  coValue?: number
  beepStatus?: string
  messageId: string
  timestamp: string
}

export function reportTelemetry(payload: TelemetryPayload): Promise<unknown> {
  return api('/api/telemetry', { method: 'POST', body: JSON.stringify(payload) })
}

export function handleAlert(id: number, action: string): Promise<unknown> {
  return api(`/api/alerts/${id}/${action}`, { method: 'POST' })
}

export function verifyAlert(id: number): Promise<ReviewResponse> {
  return api(`/api/alerts/${id}/verify`, { method: 'POST' })
}

export interface BroadcastPayload {
  deviceId: string
  content: string
  triggerAlertId: number | null
}

export function createBroadcast(payload: BroadcastPayload): Promise<BroadcastRaw> {
  return api('/api/broadcasts', { method: 'POST', body: JSON.stringify(payload) })
}

export interface UserQuery {
  keyword?: string
  role?: string
  enabled?: '' | '0' | '1'
}

export interface UserPayload {
  displayName: string
  role: UserAccount['role']
  phone: string
}

export interface CreateUserPayload extends UserPayload {
  username: string
  password: string
}

export function fetchUsers(query: UserQuery = {}): Promise<PageResult<UserAccount>> {
  const params = new URLSearchParams({ page: '1', pageSize: '200' })
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.role) params.set('role', query.role)
  if (query.enabled) params.set('enabled', query.enabled)
  return api(`/api/users?${params}`)
}

export function createUser(payload: CreateUserPayload): Promise<UserAccount> {
  return api('/api/users', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateUser(id: number, payload: UserPayload): Promise<UserAccount> {
  return api(`/api/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function updateUserStatus(id: number, enabled: boolean): Promise<UserAccount> {
  return api(`/api/users/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ enabled: enabled ? 1 : 0 }),
  })
}

export function resetUserPassword(id: number, password: string): Promise<unknown> {
  return api(`/api/users/${id}/password`, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  })
}

export function deleteUser(id: number): Promise<unknown> {
  return api(`/api/users/${id}`, { method: 'DELETE' })
}

export function deliverBroadcast(id: number): Promise<BroadcastRaw> {
  return api(`/api/broadcasts/${id}/deliver`, { method: 'POST' })
}

export function deleteBroadcast(id: number): Promise<BroadcastRaw> {
  return api(`/api/broadcasts/${id}`, { method: 'DELETE' })
}

export function chat(question: string, alertId: number | null): Promise<ChatResponse> {
  return api('/api/chat', {
    method: 'POST',
    body: JSON.stringify({ question, alertId }),
  })
}

export interface BindPayload {
  deviceId: string
  deviceName: string
  location: string
}

export function bindDevice(payload: BindPayload): Promise<BindResponse> {
  return api('/api/devices/bind', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateDevice(
  id: number,
  payload: { deviceName: string; location: string },
): Promise<unknown> {
  return api(`/api/devices/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function updateThreshold(id: number, threshold: number): Promise<unknown> {
  return api(`/api/devices/${id}/threshold`, {
    method: 'PUT',
    body: JSON.stringify({ threshold }),
  })
}

export function deleteDevice(id: number): Promise<unknown> {
  return api(`/api/devices/${id}`, { method: 'DELETE' })
}
