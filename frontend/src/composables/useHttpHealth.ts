import { computed, ref } from 'vue'

/**
 * HTTP 健康度监控（模块单例）。
 *
 * 在 axios response error 拦截器里调用 reportFailure() 上报 5xx，
 * NetworkBanner 订阅 unhealthy 在 5 分钟滚动窗口累计 ≥ 3 次时显示警告。
 */

const FAILURE_THRESHOLD = 3
const WINDOW_MS = 5 * 60 * 1000

const recentFailures = ref<number[]>([])

function trim(now: number) {
  recentFailures.value = recentFailures.value.filter((t) => now - t < WINDOW_MS)
}

export function useHttpHealth() {
  function reportFailure() {
    const now = Date.now()
    trim(now)
    recentFailures.value = [...recentFailures.value, now]
  }

  function reset() {
    recentFailures.value = []
  }

  const failureCount = computed(() => {
    trim(Date.now())
    return recentFailures.value.length
  })

  const unhealthy = computed(() => failureCount.value >= FAILURE_THRESHOLD)

  return { reportFailure, reset, failureCount, unhealthy, FAILURE_THRESHOLD, WINDOW_MS }
}
