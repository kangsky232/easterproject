import { onUnmounted, ref, watch } from 'vue'

/**
 * 数字平滑过渡：目标值变化时从当前值缓动到新值，用于 KPI / 大数字的滚动动效。
 * 返回 ref 显示值（保留一位小数）。
 */
export function useCountUp(source: () => number | null | undefined, duration = 500) {
  const display = ref('0.0')
  let raf = 0
  let current = 0

  function stop(): void {
    if (raf) cancelAnimationFrame(raf)
    raf = 0
  }

  function run(to: number): void {
    stop()
    const from = current
    const start = performance.now()
    const step = (now: number): void => {
      const progress = Math.min(1, (now - start) / duration)
      // easeOutCubic
      const eased = 1 - Math.pow(1 - progress, 3)
      current = from + (to - from) * eased
      display.value = current.toFixed(1)
      if (progress < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  watch(
    source,
    (value) => {
      const num = value === null || value === undefined ? 0 : Number(value)
      if (!Number.isFinite(num)) return
      run(num)
    },
    { immediate: true },
  )

  onUnmounted(stop)

  return display
}
