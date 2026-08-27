import { theme } from '@/theme'

export type DeviceStatus = 'online' | 'offline' | 'alarm'
export type AlarmType =
  | 'SMOKE'
  | 'OFFLINE'
  | 'TEMPERATURE'
  | 'HUMIDITY'
  | 'CURRENT'
  | 'WIRE_TEMPERATURE'
  | 'CO'
export type AlarmSeverity = 'WARNING' | 'DANGER'
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
  TEMPERATURE: { label: '环境温度', color: theme.serious },
  HUMIDITY: { label: '环境湿度', color: theme.accent },
  CURRENT: { label: '电气电流', color: theme.warning },
  WIRE_TEMPERATURE: { label: '线缆温度', color: theme.serious },
  CO: { label: '一氧化碳', color: theme.critical },
}

export const ALARM_UNIT: Record<AlarmType, string> = {
  SMOKE: 'ppm',
  OFFLINE: '',
  TEMPERATURE: '℃',
  HUMIDITY: '%',
  CURRENT: 'A',
  WIRE_TEMPERATURE: '℃',
  CO: 'ppm',
}

export const ALARM_SEVERITY: Record<AlarmSeverity, StatusMeta> = {
  WARNING: { label: '预警', color: theme.warning },
  DANGER: { label: '危险', color: theme.critical },
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

// 趋势图可选指标：与烟雾浓度共用同一数据通道（history / trend）。
export type MetricKey =
  | 'concentration'
  | 'temperature'
  | 'humidity'
  | 'current'
  | 'wireTemperature'
  | 'coValue'

export interface ChartMetric {
  key: MetricKey
  label: string
  // 单位未知的指标（设备电流、CO 值）留空，不臆测单位。
  unit: string | null
}

export const CHART_METRICS: ChartMetric[] = [
  { key: 'concentration', label: '烟雾浓度', unit: 'ppm' },
  { key: 'temperature', label: '环境温度', unit: '℃' },
  { key: 'humidity', label: '环境湿度', unit: '%' },
  { key: 'current', label: '设备电流', unit: null },
  { key: 'wireTemperature', label: '线缆温度', unit: '℃' },
  { key: 'coValue', label: 'CO 值', unit: null },
]
