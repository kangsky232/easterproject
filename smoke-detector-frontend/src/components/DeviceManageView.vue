<script setup lang="ts">
import { ref } from 'vue'
import { DEVICE_STATUS } from '@/constants'
import { theme } from '@/theme'
import type { Device } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { conc } from '@/utils/format'
import DeviceModal from './DeviceModal.vue'

const store = useDashboardStore()

const showModal = ref(false)
const editingDevice = ref<Device | null>(null)

function openAdd(): void {
  editingDevice.value = null
  showModal.value = true
}

function openEdit(device: Device): void {
  editingDevice.value = device
  showModal.value = true
}

function statusColor(device: Device): string {
  return DEVICE_STATUS[device.status]?.color ?? DEVICE_STATUS.offline.color
}

function statusLabel(device: Device): string {
  return DEVICE_STATUS[device.status]?.label ?? DEVICE_STATUS.offline.label
}

function beepLabel(status: string | null): string {
  if (!status) return '—'
  const normalized = status.toUpperCase()
  if (normalized === 'ON') return '开启'
  if (normalized === 'OFF') return '关闭'
  return status
}

// 当前浓度相对阈值着色：接近/超过阈值时提示风险。
function concColor(device: Device): string {
  const value = device.latestConcentration
  if (value == null) return theme.ink3
  const threshold = device.threshold || 100
  if (value >= threshold) return theme.critical
  if (value >= threshold * 0.8) return theme.warning
  return theme.ink1
}
</script>

<template>
  <div class="section-head">
    <h2 class="section-title">设备管理 · 设备绑定</h2>
    <button v-if="store.canManageDevices" class="btn-primary" @click="openAdd">＋ 添加设备</button>
  </div>

  <div class="table-wrap">
    <table class="device-table">
      <thead>
        <tr>
          <th>设备编码</th>
          <th>名称</th>
          <th>位置</th>
          <th>当前浓度(ppm)</th>
          <th>环境温度(℃)</th>
          <th>环境湿度(%)</th>
          <th>设备电流</th>
          <th>线缆温度(℃)</th>
          <th>CO 值</th>
          <th>蜂鸣器</th>
          <th>阈值(ppm)</th>
          <th>状态</th>
          <th v-if="store.canManageDevices">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!store.devices.length">
          <td :colspan="store.canManageDevices ? 13 : 12" class="empty">
            {{ store.canManageDevices ? '暂无设备，点击右上角“添加设备”进行绑定。' : '暂无设备。' }}
          </td>
        </tr>
        <tr v-for="device in store.devices" v-else :key="device.id">
          <td>{{ device.deviceCode }}</td>
          <td>{{ device.name || '—' }}</td>
          <td>{{ device.location || '—' }}</td>
          <td :style="{ color: concColor(device), fontWeight: 600 }">{{ conc(device.latestConcentration) }}</td>
          <td>{{ conc(device.latestTemperature) }}</td>
          <td>{{ conc(device.latestHumidity) }}</td>
          <td>{{ conc(device.latestCurrent) }}</td>
          <td>{{ conc(device.latestWireTemperature) }}</td>
          <td>{{ conc(device.latestCoValue) }}</td>
          <td>{{ beepLabel(device.latestBeepStatus) }}</td>
          <td>{{ conc(device.threshold) }}</td>
          <td><span :style="{ color: statusColor(device) }">{{ statusLabel(device) }}</span></td>
          <td v-if="store.canManageDevices">
            <button class="btn-mini" @click="openEdit(device)">编辑</button>
            <button class="btn-mini btn-mini-danger" @click="store.deleteDevice(device.id)">解绑</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <DeviceModal :open="showModal" :device="editingDevice" @close="showModal = false" />
</template>
