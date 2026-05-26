import { onBeforeUnmount, onMounted, ref } from 'vue'

export type SseHandler = (event: string, data: unknown) => void

/**
 * 订阅后端 SSE。后端固定路径 /api/stream/alerts，事件类型：
 *  - connected
 *  - ping              心跳（20s 一次）
 *  - event-created     新告警事件
 *  - event-updated     事件状态变化
 *  - ai-summary        AI 摘要生成完成
 *
 * 自动重连：
 *  - 浏览器原生 EventSource 默认会自动重连（每 ~3s），但会发 GET 请求带上 Last-Event-ID
 *  - 我们另加一层"看门狗"：超过 60s 没有任何消息（含 ping）就主动重连
 */
export function useSse(handler: SseHandler) {
  const connected = ref(false)
  let es: EventSource | undefined
  let lastMessageAt = Date.now()
  let watchdogTimer: ReturnType<typeof setInterval> | undefined

  function handle(event: string, raw: string) {
    lastMessageAt = Date.now()
    let data: unknown = raw
    try {
      data = JSON.parse(raw)
    } catch {
      // 保持原样
    }
    handler(event, data)
  }

  function connect() {
    closeQuietly()
    es = new EventSource('/api/stream/alerts')
    es.onopen = () => {
      connected.value = true
      lastMessageAt = Date.now()
    }
    es.onerror = () => {
      connected.value = false
      // 让 watchdog 决定是否手动重连；浏览器自己也会重连
    }
    const eventNames = ['connected', 'ping', 'event-created', 'event-updated', 'ai-summary']
    eventNames.forEach((name) => {
      es!.addEventListener(name, (ev) => {
        handle(name, (ev as MessageEvent).data)
      })
    })
  }

  function closeQuietly() {
    try {
      es?.close()
    } catch {
      // ignore
    }
    es = undefined
  }

  function startWatchdog() {
    watchdogTimer = setInterval(() => {
      const idleMs = Date.now() - lastMessageAt
      // 60 秒没有任何消息（远超 20s 心跳间隔），强制重连
      if (idleMs > 60_000) {
        connected.value = false
        connect()
      }
    }, 15_000)
  }

  onMounted(() => {
    connect()
    startWatchdog()
  })

  onBeforeUnmount(() => {
    if (watchdogTimer) clearInterval(watchdogTimer)
    closeQuietly()
    connected.value = false
  })

  /**
   * 主动重连：用户点 NetworkBanner 的"重新连接"按钮时触发。
   * 立即关闭旧 EventSource、重置 lastMessageAt 并发起新连接。
   */
  function reconnect() {
    connected.value = false
    lastMessageAt = Date.now()
    connect()
  }

  return { connected, reconnect }
}
