import { onBeforeUnmount, onMounted, ref } from 'vue'

export type SseHandler = (event: string, data: unknown) => void

/**
 * 订阅后端 SSE。后端固定路径 /api/stream/alerts，事件类型：
 *  - connected
 *  - event-created    新告警事件
 *  - event-updated    事件状态变化
 *  - ai-summary       AI 摘要生成完成
 */
export function useSse(handler: SseHandler) {
  const connected = ref(false)
  let es: EventSource | undefined

  function handle(event: string, raw: string) {
    let data: unknown = raw
    try {
      data = JSON.parse(raw)
    } catch {
      // 保持原样
    }
    handler(event, data)
  }

  function connect() {
    es = new EventSource('/api/stream/alerts')
    es.onopen = () => {
      connected.value = true
    }
    es.onerror = () => {
      connected.value = false
    }
    const eventNames = ['connected', 'event-created', 'event-updated', 'ai-summary']
    eventNames.forEach((name) => {
      es!.addEventListener(name, (ev) => {
        handle(name, (ev as MessageEvent).data)
      })
    })
  }

  onMounted(connect)
  onBeforeUnmount(() => {
    es?.close()
    connected.value = false
  })

  return { connected }
}
