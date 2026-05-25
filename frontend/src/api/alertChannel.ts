import { http } from './http'

export interface AlertChannelItem {
  id?: number
  channelCode?: string
  channelName: string
  channelType: string
  channelTypeName?: string
  providerName?: string
  status?: string
  priority?: number
  configJson?: string
  description?: string
  lastSendStatus?: string
  lastFailureReason?: string
  lastSentAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface AlertChannelStats {
  total: number
  enabled: number
  sentToday: number
  failedToday: number
  byType: Array<{
    channelType: string
    channelTypeName: string
    total: number
    enabled: number
  }>
}

export interface AlertNotifyLogItem {
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

export interface AlertChannelTestRequest {
  channelId: number
  receiverValue?: string
  title?: string
  content?: string
}

export function listAlertChannels(query: { channelType?: string; status?: string; keyword?: string } = {}) {
  return http.get<AlertChannelItem[]>('/alert-channels', { params: query })
}

export function saveAlertChannel(payload: AlertChannelItem) {
  return http.post<AlertChannelItem>('/alert-channels', payload)
}

export function toggleAlertChannel(id: number) {
  return http.post<AlertChannelItem>(`/alert-channels/${id}/toggle`)
}

export function deleteAlertChannel(id: number) {
  return http.delete<void>(`/alert-channels/${id}`)
}

export function testAlertChannel(payload: AlertChannelTestRequest) {
  return http.post<AlertNotifyLogItem>('/alert-channels/test', payload)
}

export function getAlertChannelStats() {
  return http.get<AlertChannelStats>('/alert-channels/stats')
}
