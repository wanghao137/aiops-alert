import { http } from './http'

export interface AlertRuleConditionItem {
  id?: number
  conditionOrder?: number
  metricCode: string
  metricName: string
  compareOp: string
  thresholdValue?: string
  thresholdUnit?: string
}

export interface AlertRuleChannelBinding {
  channelId: number
  receiverValue?: string
  templateCode?: string
}

export interface AlertRuleObjectBrief {
  id: number
  objectName: string
  objectCode: string
  objectType: string
  status: string
}

export interface AlertRuleChannelBrief {
  id: number
  channelName: string
  channelType: string
  channelTypeName: string
  status: string
  receiverValue?: string
}

export interface AlertRuleItem {
  id?: number
  ruleCode?: string
  ruleName: string
  objectType: string
  objectTypeName?: string
  conditionLogic?: string
  triggerTimes?: number
  timeWindowMinutes?: number
  minAlertIntervalMinutes?: number
  alertLevel: string
  alertLevelName?: string
  recoverNotify?: boolean
  repeatNotify?: boolean
  status?: string
  priority?: number
  notifyTitleTemplate?: string
  notifyContentTemplate?: string
  description?: string
  conditions: AlertRuleConditionItem[]
  objectIds: number[]
  objects?: AlertRuleObjectBrief[]
  channelBindings?: AlertRuleChannelBinding[]
  channels?: AlertRuleChannelBrief[]
  createdAt?: string
  updatedAt?: string
}

export interface AlertRuleStats {
  total: number
  enabled: number
  byLevel: Array<{ alertLevel: string; alertLevelName: string; total: number }>
  byType: Array<{ objectType: string; objectTypeName: string; total: number }>
}

export function listAlertRules(query: {
  objectType?: string
  alertLevel?: string
  status?: string
  keyword?: string
} = {}) {
  return http.get<AlertRuleItem[]>('/alert-rules', { params: query })
}

export function getAlertRule(id: number) {
  return http.get<AlertRuleItem>(`/alert-rules/${id}`)
}

export function saveAlertRule(payload: AlertRuleItem) {
  return http.post<AlertRuleItem>('/alert-rules', payload)
}

export function toggleAlertRule(id: number) {
  return http.post<AlertRuleItem>(`/alert-rules/${id}/toggle`)
}

export function deleteAlertRule(id: number) {
  return http.delete<void>(`/alert-rules/${id}`)
}

export function getAlertRuleStats() {
  return http.get<AlertRuleStats>('/alert-rules/stats')
}
