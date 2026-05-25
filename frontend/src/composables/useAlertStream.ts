import { onBeforeUnmount, onMounted, ref } from 'vue'

type Handler = (payload: unknown) => void

interface UseAlertStreamOptions {
  /** SSE URL，默认 /api/stream/alerts */
  url?: string
  /** 监听 event-created */
  onEventCreated?: Handler
  /** 监听 event-updated */
  onEventUpdated?: Handler
  /** 监听 ai-summary */
  onAiSummary?: Handler
  /** 是否自动重连，默认 true */
  autoReconnect?: boolean
}

/**
 * 全局共享一个 SSE 连接，多个组件都可以监听不同事件。
 */
let sharedSource: EventSource | undefined
const handlers: {
  'event-created': Set<Handler>
  'event-updated': Set<Handler>
  'ai-summary': Set<Handler>
} = {
  'event-created': new Set(),
  'event-updated': new Set(),
  'ai-summary': new Set()
}
const connected = ref(false)

function ensureConnection(url: string, autoReconnect: boolean) {
  if (sharedSource) return
  try {
    sharedSource = new EventSource(url)
    sharedSource.addEventListener('connected', () => {
      connected.value = true
    })
    sharedSource.addEventListener('event-created', (ev) => {
      const data = parseData((ev as MessageEvent).data)
      handlers['event-created'].forEach((h) => h(data))
    })
    sharedSource.addEventListener('event-updated', (ev) => {
      const data = parseData((ev as MessageEvent).data)
      handlers['event-updated'].forEach((h) => h(data))
    })
    sharedSource.addEventListener('ai-summary', (ev) => {
      const data = parseData((ev as MessageEvent).data)
      handlers['ai-summary'].forEach((h) => h(data))
    })
    sharedSource.onerror = () => {
      connected.value = false
      sharedSource?.close()
      sharedSource = undefined
      if (autoReconnect) {
        setTimeout(() => ensureConnection(url, autoReconnect), 3000)
      }
    }
  } catch (e) {
    console.warn('SSE connect failed', e)
  }
}

function parseData(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

export function useAlertStream(options: UseAlertStreamOptions = {}) {
  const url = options.url || '/api/stream/alerts'
  const autoReconnect = options.autoReconnect !== false

  onMounted(() => {
    ensureConnection(url, autoReconnect)
    if (options.onEventCreated) handlers['event-created'].add(options.onEventCreated)
    if (options.onEventUpdated) handlers['event-updated'].add(options.onEventUpdated)
    if (options.onAiSummary) handlers['ai-summary'].add(options.onAiSummary)
  })

  onBeforeUnmount(() => {
    if (options.onEventCreated) handlers['event-created'].delete(options.onEventCreated)
    if (options.onEventUpdated) handlers['event-updated'].delete(options.onEventUpdated)
    if (options.onAiSummary) handlers['ai-summary'].delete(options.onAiSummary)
  })

  return { connected }
}
