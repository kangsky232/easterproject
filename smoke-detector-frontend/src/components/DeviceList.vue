<script setup lang="ts">
import { DEVICE_STATUS } from '@/constants'
import type { Device } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { conc } from '@/utils/format'

const store = useDashboardStore()

function statusColor(device: Device): string {
  return DEVICE_STATUS[device.status]?.color ?? DEVICE_STATUS.offline.color
}

function statusLabel(device: Device): string {
  return DEVICE_STATUS[device.status]?.label ?? DEVICE_STATUS.offline.label
}

function onKey(e: KeyboardEvent, id: number): void {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    store.selectDevice(id)
  }
}
</script>

<template>
  <section class="panel" aria-label="设备总览">
    <h2 class="panel-title">设备总览</h2>
    <div v-if="store.devices.length" class="device-list">
      <div
        v-for="device in store.devices"
        :key="device.id"
        class="device-card"
        :class="{ active: device.id === store.selectedId }"
        role="button"
        tabindex="0"
        :aria-pressed="device.id === store.selectedId"
        @click="store.selectDevice(device.id)"
        @keydown="onKey($event, device.id)"
      >
        <span class="dot" :class="`dot--${device.status}`" :style="{ background: statusColor(device) }"></span>
        <div class="device-main">
          <div class="device-name" :title="device.name || device.deviceCode">
            {{ device.name || device.deviceCode }}
          </div>
          <div class="device-code">
            {{ device.deviceCode }} · <span :style="{ color: statusColor(device) }">{{ statusLabel(device) }}</span>
          </div>
        </div>
        <div class="device-conc">
          <div class="device-conc-num">{{ conc(device.latestConcentration) }}</div>
          <div class="device-conc-unit">ppm</div>
        </div>
      </div>
    </div>
    <div v-else class="empty">暂无设备</div>
  </section>
</template>
