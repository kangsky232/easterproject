import { theme } from '@/theme'

export type DeviceStatus = 'online' | 'offline' | 'alarm'
export type AlarmType = 'SMOKE' | 'OFFLINE'
export type AlarmStatus = 'pending' | 'confirmed' | 'resolved' | 'false_alarm'

export interface StatusMeta {
  label: string
  color: string
}

export const DEVICE_STATUS: Record<DeviceStatus, StatusMeta> = {
  online: { label: '在线', color: theme.good },
  offline: { label: '离线', color: theme.serious },
  alarm: { label: '告警中', color: theme.critical },
}

export const ALARM_TYPE: Record<AlarmType, StatusMeta> = {
  SMOKE: { label: '烟雾', color: theme.critical },
  OFFLINE: { label: '离线', color: theme.warning },
}

export const ALARM_STATUS: Record<AlarmStatus, StatusMeta> = {
  pending: { label: '待处理', color: theme.warning },
  confirmed: { label: '已确认', color: theme.serious },
  resolved: { label: '已处置', color: theme.good },
  false_alarm: { label: '误报', color: theme.ink3 },
}

export const ROLE_LABEL: Record<string, string> = {
  RESIDENT: '居民',
  COMMUNITY_ADMIN: '小区管理员',
  SYSTEM_ADMIN: '系统管理员',
  FIREFIGHTER: '消防员',
}

export const BROADCAST_STATUS: Record<number, string> = {
  0: '待下发',
  1: '已完成',
  2: '下发失败',
}

export const TOKEN_KEY = 'smart-smoke.token'
export const USER_KEY = 'smart-smoke.user'

export const MAX_CHART_POINTS = 120
export const REFRESH_MS = 10_000
export const MAX_REFRESH_MS = 60_000
