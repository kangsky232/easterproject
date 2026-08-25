const pad = (value: number) => String(value).padStart(2, '0')

/** 烟雾浓度展示：空值显示 --，其余保留 2 位小数。 */
export function conc(value: number | null | undefined): string {
  return value === null || value === undefined ? '--' : Number(value).toFixed(2)
}

/** ISO 时间 → HH:mm:ss，解析失败时原样返回。 */
export function fmtClock(iso?: string | null): string {
  if (!iso) return '--'
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** ISO 时间 → MM-DD HH:mm，解析失败时原样返回。 */
export function fmtDate(iso?: string | null): string {
  if (!iso) return '--'
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** ISO 时间 → YYYY-MM-DD HH:mm:ss，用于跨天趋势轴。 */
export function fmtFull(iso?: string | null): string {
  if (!iso) return '--'
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 趋势图 X 轴标签：跨度超过 26 小时时显示「MM-DD HH:mm」，否则显示「HH:mm」，
 * 避免跨天时出现多个重复的 00:00。
 */
export function fmtTrendTime(iso: string, spanHours: number): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  if (spanHours > 26) return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 生成指定日期的本地时区 "YYYY-MM-DDTHH:mm:ss" 字符串（不含 Z，供后端 LocalDateTime 解析）。 */
export function toLocalIso(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

/** 生成本地时区的 "YYYY-MM-DDTHH:mm:ss" 字符串，用于上报遥测数据。 */
export function localTimestamp(): string {
  return toLocalIso(new Date())
}
