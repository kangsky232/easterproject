<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { CHART_METRICS } from '@/constants'
import { chartTheme, isDark } from '@/theme'
import { useDashboardStore } from '@/store/dashboard'

const emit = defineEmits<{ (e: 'open'): void }>()

const store = useDashboardStore()
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const activeMetric = computed(
  () => CHART_METRICS.find((item) => item.key === store.selectedMetric) ?? CHART_METRICS[0],
)

function render(): void {
  if (!chart) return
  const key = store.selectedMetric
  const values = store.chartSeries[key] ?? []
  const meta = activeMetric.value
  const data = store.chartTimes.map((iso, i) => {
    const ts = new Date(iso).getTime()
    const value = values[i]
    return [ts, value == null ? null : value]
  })

  chart.setOption(
    {
      backgroundColor: 'transparent',
      grid: { left: 46, right: 16, top: 22, bottom: 30 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: chartTheme.surface,
        borderColor: chartTheme.border,
        textStyle: { color: chartTheme.text, fontSize: 12 },
        axisPointer: { type: 'line', lineStyle: { color: chartTheme.axis, width: 1 } },
        formatter: (params: unknown) => {
          const p = (params as any[])[0]
          if (!p) return ''
          const time = new Date(p.value[0])
          const timeStr = `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
          const val = p.value[1]
          const unit = meta.unit ?? ''
          if (val == null) return `${timeStr}<br/>${meta.label}：--`
          return `${timeStr}<br/>${meta.label}：<b>${Number(val).toFixed(2)}${unit}</b>`
        },
      },
      xAxis: {
        type: 'time',
        axisLabel: { color: chartTheme.axis, fontSize: 10, hideOverlap: true },
        axisLine: { lineStyle: { color: chartTheme.axis } },
        axisTick: { show: false },
        splitLine: { show: true, lineStyle: { color: chartTheme.grid } },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: chartTheme.grid } },
        axisLabel: { color: chartTheme.axis, fontSize: 10 },
      },
      series: [
        {
          name: meta.label,
          type: 'line',
          data,
          showSymbol: false,
          smooth: false,
          lineStyle: { width: 2, color: chartTheme.line },
          areaStyle: { color: chartTheme.area },
        },
      ],
    },
    true,
  )
}

function resize(): void {
  chart?.resize()
}

function onOpen(): void {
  emit('open')
}

onMounted(() => {
  if (chartEl.value) chart = echarts.init(chartEl.value)
  window.addEventListener('resize', resize)
  render()
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(
  [() => store.chartTimes, () => store.chartSeries, () => store.selectedMetric, () => isDark.value],
  () => render(),
)
</script>

<template>
  <div
    class="mini-chart"
    role="button"
    tabindex="0"
    :aria-label="`查看${activeMetric.label}趋势详情`"
    @click="onOpen"
    @keydown.enter="onOpen"
    @keydown.space.prevent="onOpen"
  >
    <div class="mini-chart__head">
      <span class="mini-chart__title">{{ activeMetric.label }}趋势</span>
      <span class="mini-chart__cta">查看详情 →</span>
    </div>
    <div ref="chartEl" class="mini-chart__canvas"></div>
  </div>
</template>
