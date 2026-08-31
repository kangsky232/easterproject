// 数据可视化大屏配色 · 单一数据源
// 与 style.css 的 :root / html.dark 变量保持一致，改主题时两处同步（值以本文件为准）。

import { reactive, ref } from 'vue'

// 白天 / 黑夜模式（模块级单例，跨组件共享）。
export const isDark = ref(false)

// 浅色页面 token（白天模式）
export const theme = {
  page: '#f7fbff',
  surface: '#ffffff',
  ink1: '#0f2a52',
  ink2: '#46597a',
  ink3: '#8ba0c2',
  grid: '#e3ecf7',
  baseline: '#cdd9ea',
  border: 'rgba(15, 42, 82, 0.12)',
  accent: '#2563eb',
  good: '#1fbf75',
  warning: '#f2a93b',
  serious: '#e8845a',
  critical: '#e5484d',
} as const

// 折线图配色（浅蓝底色）
const chartLight = {
  bg: '#eaf3ff',               // 浅蓝底
  surface: '#ffffff',          // tooltip 背景
  line: '#2563eb',             // 蓝折线
  grid: 'rgba(15, 42, 82, 0.10)',
  axis: 'rgba(15, 42, 82, 0.55)',
  area: 'rgba(37, 99, 235, 0.14)',
  text: 'rgba(15, 42, 82, 0.85)',
  border: 'rgba(15, 42, 82, 0.18)',
  sliderFiller: 'rgba(37, 99, 235, 0.18)',
}

// 折线图配色（黑夜模式：深色底）
const chartDark = {
  bg: '#0d1a2f',               // 深蓝黑底
  surface: '#173d6e',
  line: '#ffffff',
  grid: 'rgba(255, 255, 255, 0.14)',
  axis: 'rgba(255, 255, 255, 0.55)',
  area: 'rgba(255, 255, 255, 0.12)',
  text: 'rgba(255, 255, 255, 0.85)',
  border: 'rgba(255, 255, 255, 0.18)',
  sliderFiller: 'rgba(255, 255, 255, 0.16)',
}

// 图表配色：随 isDark 切换，组件里直接读取即可（reactive 对象）。
export const chartTheme = reactive<Record<string, string>>({ ...chartLight })

const THEME_KEY = 'smart-smoke.theme'

function applyTheme(dark: boolean): void {
  isDark.value = dark
  const next = dark ? chartDark : chartLight
  Object.keys(chartTheme).forEach((key) => {
    chartTheme[key] = next[key as keyof typeof chartLight]
  })
  if (typeof document !== 'undefined') {
    document.documentElement.classList.toggle('dark', dark)
  }
}

export function initTheme(): void {
  try {
    applyTheme(localStorage.getItem(THEME_KEY) === 'dark')
  } catch {
    applyTheme(false)
  }
}

export function toggleTheme(): void {
  const next = !isDark.value
  try {
    localStorage.setItem(THEME_KEY, next ? 'dark' : 'light')
  } catch {
    /* 忽略存储失败 */
  }
  applyTheme(next)
}

export type ThemeColor = keyof typeof theme
