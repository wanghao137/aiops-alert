import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import type { AlertEventItem } from '@/api/alertEvent'
import { getAlertLevelMeta } from '@/utils/alertLevel'

/**
 * 全局实时事件中心：所有页面共享一个 store；
 *  - 累积最近 50 条 SSE 推送的事件，供事件中心顶部"实时流"展示
 *  - 收到新事件时弹 ElNotification toast
 *  - 收到 AI 摘要完成时，更新该事件的 aiSummary
 */
export const useRealtimeStore = defineStore('realtime', () => {
  const recentEvents = ref<AlertEventItem[]>([])
  const lastEventId = ref<number>()
  const aiSummaryUpdates = ref(0)

  function pushEvent(ev: AlertEventItem) {
    recentEvents.value.unshift(ev)
    if (recentEvents.value.length > 50) recentEvents.value.length = 50
    lastEventId.value = ev.id

    const meta = getAlertLevelMeta(ev.alertLevel)
    ElNotification({
      title: ev.eventTitle || '新告警',
      message: `${ev.objectName} · ${ev.metricName}`,
      type: ev.alertLevel === 'CRITICAL' ? 'error'
            : ev.alertLevel === 'SERIOUS' ? 'warning'
            : 'info',
      position: 'top-right',
      duration: 5000,
      customClass: 'aiops-alert-toast'
    })
    void meta
  }

  function updateEventStatus(ev: AlertEventItem) {
    const idx = recentEvents.value.findIndex((e) => e.id === ev.id)
    if (idx >= 0) recentEvents.value[idx] = { ...recentEvents.value[idx], ...ev }
  }

  function applySummary(eventId: number, summary?: string, status = 'SUCCESS', reasoning?: string) {
    aiSummaryUpdates.value += 1
    const idx = recentEvents.value.findIndex((e) => e.id === eventId)
    if (idx >= 0) {
      recentEvents.value[idx] = {
        ...recentEvents.value[idx],
        aiSummary: summary,
        aiSummaryStatus: status,
        aiReasoning: reasoning
      }
    }
  }

  return { recentEvents, lastEventId, aiSummaryUpdates, pushEvent, updateEventStatus, applySummary }
})
