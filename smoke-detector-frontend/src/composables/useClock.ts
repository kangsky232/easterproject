import { onMounted, onUnmounted, ref } from 'vue'

/** 每秒更新一次的当前时间，用于顶部时钟。 */
export function useClock() {
  const now = ref(new Date())
  let timer: number | undefined

  onMounted(() => {
    timer = window.setInterval(() => {
      now.value = new Date()
    }, 1000)
  })

  onUnmounted(() => window.clearInterval(timer))

  return now
}
