<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { DEVICE_STATUS } from '@/constants'
import { theme } from '@/theme'
import { useDashboardStore } from '@/store/dashboard'
import { useCountUp } from '@/composables/useCountUp'
import { conc } from '@/utils/format'

const store = useDashboardStore()
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const device = computed(() => store.selectedDevice)
const statusMeta = computed(() => DEVICE_STATUS[device.value?.status ?? 'offline'])
const heroDisplay = useCountUp(() => device.value?.latestConcentration, 500, 2)

const RANGES = [
  { hours: 0, label: '实时' },
  { hours: 24, label: '24小时' },
  { hours: 24 * 7, label: '7天' },
  { hours: 24 * 30, label: '30天' },
]

function render(): void {
  if (!chart) return
  const threshold = device.value?.threshold ?? 2000

  // 构造 [时间戳, 浓度] 数据
  const data = store.chartTimes.map((iso, i) => {
    const ts = new Date(iso).getTime()
    return [ts, store.chartValues[i] ?? 0]
  })

  chart.setOption(
    {
      backgroundColor: 'transparent',
      grid: { left: 52, right: 18, top: 24, bottom: 52 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: theme.surface,
        borderColor: theme.border,
        textStyle: { color: theme.ink1, fontSize: 12 },
        axisPointer: { type: 'line', lineStyle: { color: theme.ink3, width: 1 } },
        formatter: (params: unknown) => {
          const p = (params as any[])[0]
          if (!p) return ''
          const time = new Date(p.value[0])
          const timeStr = `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}:${String(time.getSeconds()).padStart(2, '0')}`
          const val = p.value[1]
          return `${timeStr}<br/>烟雾浓度：<b>${conc(val)} ppm</b>`
        },
      },
      xAxis: {
        type: 'time',
        interval: store.trendHours === 0 ? 10_000 : undefined,
        minInterval: store.trendHours === 0 ? 10_000 : 60_000,
        axisLabel: {
          color: theme.ink3,
          fontSize: 11,
          hideOverlap: true,
          formatter: (value: number) => {
            const d = new Date(value)
            const minute = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
            return store.trendHours === 0
              ? `${minute}:${String(d.getSeconds()).padStart(2, '0')}`
              : minute
          },
        },
        axisLine: { lineStyle: { color: theme.baseline } },
        axisTick: { show: false },
        splitLine: {
          show: true,
          lineStyle: { color: theme.grid },
        },
      },
      yAxis: {
        type: 'value',
        name: 'ppm',
        nameTextStyle: { color: theme.ink3 },
        splitLine: { lineStyle: { color: theme.grid } },
        axisLabel: { color: theme.ink3, fontSize: 11 },
      },
      dataZoom: [
        { type: 'inside', throttle: 50 },
        {
          type: 'slider',
          height: 16,
          bottom: 6,
          borderColor: 'transparent',
          backgroundColor: 'rgba(255,255,255,0.04)',
          fillerColor: 'rgba(57,135,229,0.15)',
          handleStyle: { color: theme.accent },
          moveHandleStyle: { color: theme.accent },
          textStyle: { color: theme.ink3, fontSize: 10 },
        },
      ],
      series: [
        {
          name: '烟雾浓度',
          type: 'line',
          data: data,                               // 使用二维数组
          showSymbol: false,
          smooth: false,
          lineStyle: { width: 2, color: theme.accent },
          areaStyle: { color: 'rgba(57,135,229,0.10)' },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: theme.critical, type: 'dashed', width: 1 },
            label: { color: theme.ink2, fontSize: 11, formatter: `阈值 ${conc(threshold)}` },
            data: [{ yAxis: threshold }],
          },
        },
      ],
    },
    true,
  )
}

function resize(): void {
  chart?.resize()
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
  [() => store.chartTimes, () => store.chartValues, () => store.selectedThreshold],
  () => render(),
)
</script>

<template>
  <section class="panel panel-center" aria-label="实时浓度趋势">
    <div class="panel-head">
      <h2 class="panel-title">实时浓度趋势</h2>
      <div class="range-switch" role="group" aria-label="时间范围">
        <button
          v-for="range in RANGES"
          :key="range.hours"
          type="button"
          class="range-btn"
          :class="{ active: store.trendHours === range.hours }"
          @click="store.setTrendHours(range.hours)"
        >
          {{ range.label }}
        </button>
      </div>
    </div>

    <div class="hero">
      <div class="hero-value">
        {{ device?.latestConcentration == null ? '--' : heroDisplay }}
      </div>
      <div class="hero-unit">ppm</div>
      <div v-if="!device" class="hero-meta">选择设备查看</div>
      <div v-else class="hero-meta">
        {{ device.name || device.deviceCode }} · 阈值 {{ conc(device.threshold) }} ppm ·
        <span :style="{ color: statusMeta.color }">{{ statusMeta.label }}</span> · 每10秒刷新
      </div>
    </div>
    <div ref="chartEl" class="chart"></div>
  </section>
</template>
