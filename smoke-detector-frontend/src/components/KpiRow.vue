<script setup lang="ts">
import { useDashboardStore } from '@/store/dashboard'
import { useCountUp } from '@/composables/useCountUp'

const store = useDashboardStore()

function valueOf(key: string): number {
  return store.kpiItems.find((item) => item.key === key)?.value ?? 0
}

const counts: Record<string, ReturnType<typeof useCountUp>> = {
  total_devices: useCountUp(() => valueOf('total_devices')),
  online: useCountUp(() => valueOf('online')),
  offline: useCountUp(() => valueOf('offline')),
  alarm: useCountUp(() => valueOf('alarm')),
  unhandled_alarms: useCountUp(() => valueOf('unhandled_alarms')),
}
</script>

<template>
  <section class="kpi-row">
    <div v-for="item in store.kpiItems" :key="item.key" class="kpi">
      <div class="kpi-label">
        <span class="kpi-dot" :style="{ background: item.color }"></span>{{ item.label }}
      </div>
      <div class="kpi-value" :style="{ color: item.color }">{{ counts[item.key].value }}</div>
    </div>
  </section>
</template>
