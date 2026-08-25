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
  NotificationRaw,
  OverviewRaw,
  PageResult,
  ReviewResponse,
  SystemCapabilities,
  TrendPointRaw,
} from './types'

export function checkHealth(): Promise<unknown> {
  return api('/api/health')
}

export function fetchCapabilities(): Promise<SystemCapabilities> {
  return api('/api/system/capabilities')
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

export function createBroadcast(payload: BroadcastPayload): Promise<unknown> {
  return api('/api/broadcasts', { method: 'POST', body: JSON.stringify(payload) })
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
