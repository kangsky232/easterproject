// 数据可视化大屏配色 · 单一数据源
// 与 style.css 的 :root 变量保持一致，改主题时两处同步（值以本文件为准）。

export const theme = {
  page: '#0d0d0d',
  surface: '#1a1a19',
  ink1: '#ffffff',
  ink2: '#c3c2b7',
  ink3: '#898781',
  grid: '#2c2c2a',
  baseline: '#383835',
  border: 'rgba(255, 255, 255, 0.10)',
  accent: '#3987e5',
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
} as const

export type ThemeColor = keyof typeof theme
