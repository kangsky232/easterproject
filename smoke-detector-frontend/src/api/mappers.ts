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
    latestTemperature: raw.latestTemperature ?? null,
    latestHumidity: raw.latestHumidity ?? null,
    latestCurrent: raw.latestCurrent ?? null,
    latestWireTemperature: raw.latestWireTemperature ?? null,
    latestCoValue: raw.latestCoValue ?? null,
    latestBeepStatus: raw.latestBeepStatus ?? null,
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

const ALARM_TYPE_MAP = {
  1: 'SMOKE',
  2: 'OFFLINE',
  3: 'TEMPERATURE',
  4: 'HUMIDITY',
  5: 'CURRENT',
  6: 'WIRE_TEMPERATURE',
  7: 'CO',
} as const

export function toAlarm(raw: AlarmRaw): Alarm {
  return {
    id: raw.id,
    deviceCode: raw.deviceId,
    alarmType: ALARM_TYPE_MAP[raw.alertType as keyof typeof ALARM_TYPE_MAP] ?? 'SMOKE',
    currentValue: raw.concentration ?? null,
    thresholdValue: raw.threshold ?? null,
    severity: raw.severity ?? null,
    ruleDescription: raw.ruleDescription ?? null,
    status:
      raw.falseAlarm === 1 || raw.falseAlarm === true
        ? 'false_alarm'
        : ALARM_STATUS_MAP[raw.status] ?? 'pending',
    createdAt: raw.createdAt ?? null,
  }
}
