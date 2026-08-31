<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import TrendChart from '@/components/TrendChart.vue'

const emit = defineEmits<{ (e: 'back'): void }>()

const store = useDashboardStore()
const device = computed(() => store.selectedDevice)
</script>

<template>
  <div class="trend-detail">
    <header class="trend-detail__bar">
      <button type="button" class="trend-back" @click="emit('back')">← 返回首页</button>
      <div class="trend-detail__title">
        <h2>趋势详情</h2>
        <p v-if="device">{{ device.name || device.deviceCode }} · {{ device.deviceCode }}</p>
        <p v-else>选择设备后查看趋势</p>
      </div>
    </header>

    <div class="trend-detail__chart">
      <TrendChart />
    </div>
  </div>
</template>

<style scoped>
.trend-detail { min-height: 100vh; }

.trend-detail__bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.trend-back {
  background: none;
  border: 1px solid var(--border);
  color: var(--ink-2);
  font-size: 13px;
  padding: 8px 14px;
  border-radius: 8px;
  cursor: pointer;
  white-space: nowrap;
  transition: border-color 0.15s, color 0.15s;
}
.trend-back:hover { border-color: var(--accent); color: var(--accent); }

.trend-detail__title h2 { font-size: 18px; font-weight: 700; }
.trend-detail__title p { margin-top: 3px; color: var(--ink-3); font-size: 12px; }

.trend-detail__chart {
  margin-top: 16px;
  /* 放大折线图：覆盖 TrendChart 内部 .chart 的默认高度 */
}
.trend-detail__chart :deep(.panel-center) { min-height: 72vh; }
.trend-detail__chart :deep(.chart) { min-height: 60vh; max-height: none; }
</style>
