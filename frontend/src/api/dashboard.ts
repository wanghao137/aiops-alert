import { http } from './http'
import type { AlertEventItem } from './alertEvent'

export interface DashboardStatItem {
  code: string
  name: string
  value: number
}

export interface DashboardTrendItem {
  date: string
  total: number
  pending: number
  recovered: number
  critical: number
}

export interface DashboardRuleHitItem {
  ruleId: number
  ruleName: string
  objectType: string
  hitCount: number
}

export interface DashboardData {
  objectTotal: number
  enabledObjectTotal: number
  ruleTotal: number
  enabledRuleTotal: number
  channelTotal: number
  enabledChannelTotal: number
  eventTotal: number
  pendingEventTotal: number
  seriousEventTotal: number
  criticalEventTotal: number
  notifyFailedToday: number
  openIncidentTotal: number
  statusDistribution: DashboardStatItem[]
  levelDistribution: DashboardStatItem[]
  objectTypeDistribution: DashboardStatItem[]
  sevenDayTrend: DashboardTrendItem[]
  ruleHitTop: DashboardRuleHitItem[]
  recentEvents: AlertEventItem[]
}

export function getDashboard() {
  return http.get<DashboardData>('/dashboard')
}
