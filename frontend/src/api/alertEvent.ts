import { http } from './http'

export interface AlertEventHandleLog {
  id: number
  eventId: number
  actionType: string
  actionTypeName?: string
  beforeStatus?: string
  afterStatus?: string
  operatorName?: string
  operatorPhone?: string
  actionComment?: string
  createdAt?: string
}

export interface AlertNotifyLog {
  id: number
  eventId?: number
  ruleId?: number
  channelId: number
  channelType: string
  receiverValue?: string
  notifyTitle?: string
  notifyContent?: string
  sendStatus: string
  providerMsgId?: string
  failureReason?: string
  sentAt?: string
  createdAt?: string
}

export interface AiSummary {
  what?: string
  impact?: string
  causes?: string[]
  actions?: string[]
  error?: string
}

export interface AlertEventItem {
  id: number
  eventNo: string
  incidentId?: number
  ruleId: number
  ruleName?: string
  objectId: number
  objectType: string
  objectName: string
  metricCode: string
  metricName: string
  alertLevel: string
  eventStatus: string
  currentValue?: string
  thresholdValue?: string
  eventTitle: string
  eventContent?: string
  eventReason?: string
  aiSummary?: string
  aiSummaryStatus?: string
  firstTriggeredAt?: string
  lastTriggeredAt?: string
  confirmedAt?: string
  recoveredAt?: string
  closedAt?: string
  createdAt?: string
  updatedAt?: string
  handleLogs?: AlertEventHandleLog[]
  notifyLogs?: AlertNotifyLog[]
}

export function listAlertEvents(query: {
  objectType?: string
  alertLevel?: string
  eventStatus?: string
  keyword?: string
  limit?: number
} = {}) {
  return http.get<AlertEventItem[]>('/alert-events', { params: query })
}

export function getAlertEvent(id: number) {
  return http.get<AlertEventItem>(`/alert-events/${id}`)
}

export function handleAlertEvent(payload: {
  eventId: number
  actionType: 'CONFIRM' | 'RECOVER' | 'CLOSE' | 'COMMENT'
  operatorName?: string
  operatorPhone?: string
  actionComment?: string
}) {
  return http.post<AlertEventItem>('/alert-events/action', payload)
}

export function createTestAlertEvent(payload: {
  ruleId: number
  objectId: number
  currentValue?: string
  eventReason?: string
}) {
  return http.post<AlertEventItem>('/alert-events/test', payload)
}

export function summarizeAlertEvent(id: number) {
  return http.post<void>(`/alert-events/${id}/summarize`)
}

export function listEventNotifyLogs(eventId: number) {
  return http.get<AlertNotifyLog[]>(`/alert-events/${eventId}/notify-logs`)
}

export function retryNotify(notifyLogId: number) {
  return http.post<AlertNotifyLog>('/alert-events/notify-logs/retry', { notifyLogId })
}

export function parseAiSummary(raw?: string): AiSummary | undefined {
  if (!raw) return undefined
  try {
    return JSON.parse(raw) as AiSummary
  } catch {
    return undefined
  }
}
