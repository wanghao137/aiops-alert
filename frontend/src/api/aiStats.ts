import { http } from './http'

export interface SceneStat {
  scene: string
  callCount: number
  tokenTotal: number
  tokenPercent: number
}

export interface TrendItem {
  date: string
  callCount: number
}

export interface AiStatsOverview {
  todayCallTotal: number
  todayTokenTotal: number
  todaySuccessRate: number
  yesterdayCallTotal: number
  yesterdayTokenTotal: number
  yesterdaySuccessRate: number
  sceneDistribution: SceneStat[]
  sevenDayTrend: TrendItem[]
  todayCost: number | string
  monthCost: number | string
  costCurrency: string
}

export interface AiCallLogItem {
  id: number
  scene: string
  modelConfigId?: number
  modelName?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  durationMs?: number
  status?: string
  errorMessage?: string
  /** 仅详情接口返回 */
  reasoningContent?: string
  /** 仅详情接口返回 */
  requestPayload?: string
  /** 仅详情接口返回 */
  responsePayload?: string
  estimatedCost?: number | string
  createdAt?: string
}

export interface AiCallLogPage {
  total: number
  page: number
  size: number
  records: AiCallLogItem[]
}

export interface AiCallLogQuery {
  scene?: string
  modelName?: string
  status?: string
  page?: number
  size?: number
}

export function getAiStatsOverview() {
  return http.get<AiStatsOverview>('/ai-stats/overview')
}

export function listSlowAiCalls(params: { days?: number; limit?: number } = {}) {
  return http.get<AiCallLogItem[]>('/ai-stats/slow', { params })
}

export function listAiCallLogs(params: AiCallLogQuery = {}) {
  return http.get<AiCallLogPage>('/ai-stats/logs', { params })
}

export function getAiCallLog(id: number) {
  return http.get<AiCallLogItem>(`/ai-stats/logs/${id}`)
}
