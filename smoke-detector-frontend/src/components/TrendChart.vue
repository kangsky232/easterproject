<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { CHART_METRICS, DEVICE_STATUS, type MetricKey } from '@/constants'
import { theme, chartTheme, isDark } from '@/theme'
import { useDashboardStore } from '@/store/dashboard'
import { useCountUp } from '@/composables/useCountUp'
import { conc } from '@/utils/format'

const store = useDashboardStore()
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const device = computed(() => store.selectedDevice)
const statusMeta = computed(() => DEVICE_STATUS[device.value?.status ?? 'offline'])

const activeMetric = computed(
  () => CHART_METRICS.find((item) => item.key === store.selectedMetric) ?? CHART_METRICS[0],
)

function metric(value: number | null | undefined): string {
  return value == null ? '--' : Number(value).toFixed(2)
}

function beepLabel(status: string | null | undefined): string {
  if (!status) return '--'
  const normalized = status.toUpperCase()
  if (normalized === 'ON') return '开启'
  if (normalized === 'OFF') return '关闭'
  return status
}

function latestValue(key: MetricKey): number | null | undefined {
  const d = device.value
  if (!d) return undefined
  switch (key) {
    case 'concentration':
      return d.latestConcentration
    case 'temperature':
      return d.latestTemperature
    case 'humidity':
      return d.latestHumidity
    case 'current':
      return d.latestCurrent
    case 'wireTemperature':
      return d.latestWireTemperature
    case 'coValue':
      return d.latestCoValue
  }
}

const heroValue = computed(() => latestValue(store.selectedMetric))
const heroDisplay = useCountUp(() => heroValue.value, 500, 2)

function selectMetric(key: MetricKey): void {
  store.selectMetric(key)
}

const RANGES = [
  { hours: 0, label: '实时' },
  { hours: 24, label: '24小时' },
  { hours: 24 * 7, label: '7天' },
  { hours: 24 * 30, label: '30天' },
]

function render(): void {
  if (!chart) return
  const key = store.selectedMetric
  const values = store.chartSeries[key] ?? []
  const meta = activeMetric.value
  const threshold = device.value?.threshold ?? 100
  const showThreshold = key === 'concentration'

  // 构造 [时间戳, 数值] 数据；空值保留为 null，使折线断点而非误读为 0。
  const data = store.chartTimes.map((iso, i) => {
    const ts = new Date(iso).getTime()
    const value = values[i]
    return [ts, value == null ? null : value]
  })

  chart.setOption(
    {
      backgroundColor: 'transparent',
      grid: { left: 52, right: 18, top: 24, bottom: 52 },
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
          const timeStr = `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}:${String(time.getSeconds()).padStart(2, '0')}`
          const val = p.value[1]
          const unit = meta.unit ?? ''
          if (val == null) return `${timeStr}<br/>${meta.label}：--`
          return `${timeStr}<br/>${meta.label}：<b>${Number(val).toFixed(2)}${unit}</b>`
        },
      },
      xAxis: {
        type: 'time',
        interval: store.trendHours === 0 ? 3_000 : undefined,
        minInterval: store.trendHours === 0 ? 3_000 : 60_000,
        axisLabel: {
          color: chartTheme.axis,
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
        axisLine: { lineStyle: { color: chartTheme.axis } },
        axisTick: { show: false },
        splitLine: {
          show: true,
          lineStyle: { color: chartTheme.grid },
        },
      },
      yAxis: {
        type: 'value',
        name: meta.unit ?? '',
        nameTextStyle: { color: chartTheme.axis },
        splitLine: { lineStyle: { color: chartTheme.grid } },
        axisLabel: { color: chartTheme.axis, fontSize: 11 },
      },
      dataZoom: [
        { type: 'inside', throttle: 50 },
        {
          type: 'slider',
          height: 16,
          bottom: 6,
          borderColor: 'transparent',
          backgroundColor: 'rgba(255,255,255,0.04)',
          fillerColor: chartTheme.sliderFiller,
          handleStyle: { color: chartTheme.line },
          moveHandleStyle: { color: chartTheme.line },
          textStyle: { color: chartTheme.axis, fontSize: 10 },
        },
      ],
      series: [
        {
          name: meta.label,
          type: 'line',
          data: data,
          showSymbol: false,
          smooth: false,
          lineStyle: { width: 2, color: chartTheme.line },
          areaStyle: { color: chartTheme.area },
          markLine: showThreshold
            ? {
                silent: true,
                symbol: 'none',
                lineStyle: { color: theme.critical, type: 'dashed', width: 1 },
                label: { color: chartTheme.text, fontSize: 11, formatter: `阈值 ${conc(threshold)}` },
                data: [{ yAxis: threshold }],
              }
            : undefined,
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
  [() => store.chartTimes, () => store.chartSeries, () => store.selectedMetric, () => store.selectedThreshold, () => isDark.value],
  () => render(),
)
</script>

<template>
  <section class="panel panel-center" aria-label="传感器趋势">
    <div class="panel-head">
      <h2 class="panel-title">{{ activeMetric.label }}趋势</h2>
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
        {{ heroValue == null ? '--' : heroDisplay }}
      </div>
      <div v-if="activeMetric.unit" class="hero-unit">{{ activeMetric.unit }}</div>
      <div v-if="!device" class="hero-meta">选择设备查看</div>
      <div v-else class="hero-meta">
        {{ device.name || device.deviceCode }} · {{ activeMetric.label }}
        <template v-if="store.selectedMetric === 'concentration'"> · 阈值 {{ conc(device.threshold) }} ppm</template>
        ·
        <span :style="{ color: statusMeta.color }">{{ statusMeta.label }}</span> · 每3秒刷新
      </div>
    </div>

    <div class="sensor-metrics" aria-label="实时传感器数据">
      <button
        v-for="m in CHART_METRICS"
        :key="m.key"
        type="button"
        class="sensor-metric"
        :class="{ 'is-active': store.selectedMetric === m.key }"
        :aria-pressed="store.selectedMetric === m.key"
        @click="selectMetric(m.key)"
      >
        <span>{{ m.label }}</span>
        <strong>{{ metric(latestValue(m.key)) }}</strong>
        <small v-if="m.unit">{{ m.unit }}</small>
      </button>
      <div class="sensor-metric sensor-metric--static">
        <span>蜂鸣器</span>
        <strong class="beep-status">{{ beepLabel(device?.latestBeepStatus) }}</strong>
      </div>
    </div>

    <div ref="chartEl" class="chart"></div>
  </section>
</template>

<style scoped>
.sensor-metrics {
  display: grid;
  grid-template-columns: repeat(7, minmax(84px, 1fr));
  gap: 8px;
  margin: 8px 0 4px;
}

.sensor-metric {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.02);
  white-space: nowrap;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

button.sensor-metric:hover {
  border-color: rgba(255, 255, 255, 0.22);
}

.sensor-metric.is-active {
  border-color: var(--accent);
  background: rgba(57, 135, 229, 0.12);
}

.sensor-metric.is-active span {
  color: var(--accent);
}

.sensor-metric--static {
  cursor: default;
}

.sensor-metric span {
  display: block;
  margin-bottom: 4px;
  overflow: hidden;
  color: var(--ink-3);
  font-size: 11px;
  text-overflow: ellipsis;
}

.sensor-metric strong {
  color: var(--ink-1);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.sensor-metric small {
  margin-left: 3px;
  color: var(--ink-3);
  font-size: 10px;
}

.sensor-metric .beep-status {
  color: var(--good);
}

@media (max-width: 1280px) {
  .sensor-metrics { grid-template-columns: repeat(4, minmax(84px, 1fr)); }
}

@media (max-width: 640px) {
  .sensor-metrics { grid-template-columns: repeat(2, minmax(84px, 1fr)); }
}
</style>
