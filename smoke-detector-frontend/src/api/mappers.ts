import type { AlarmRaw, DeviceRaw, Alarm, Device } from './types'
import type { AlarmStatus } from '@/constants'

export function toDevice(raw: DeviceRaw): Device {
  return {
    id: raw.id,
    deviceCode: raw.deviceId,
    name: raw.deviceName ?? '',
    location: raw.location ?? '',
    threshold: raw.threshold,
    battery: raw.battery ?? null,
    latestConcentration: raw.latestConcentration ?? null,
    latestTime: raw.latestTimestamp ?? null,
    online: raw.online,
    status: raw.online ? 'online' : 'offline',
  }
}

const ALARM_STATUS_MAP: Record<number, AlarmStatus> = {
  0: 'pending',
  1: 'confirmed',
  2: 'resolved',
}

export function toAlarm(raw: AlarmRaw): Alarm {
  return {
    id: raw.id,
    deviceCode: raw.deviceId,
    alarmType: raw.alertType === 1 ? 'SMOKE' : 'OFFLINE',
    currentValue: raw.concentration ?? null,
    thresholdValue: raw.threshold ?? null,
    status:
      raw.falseAlarm === 1 || raw.falseAlarm === true
        ? 'false_alarm'
        : ALARM_STATUS_MAP[raw.status] ?? 'pending',
    createdAt: raw.createdAt ?? null,
  }
}
