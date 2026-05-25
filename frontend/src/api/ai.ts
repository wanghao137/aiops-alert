import { http } from './http'
import type { AlertRuleItem } from './alertRule'

export interface Nl2RuleResult {
  draft: AlertRuleItem
  rawOutput?: string
  notes?: string[]
  objectMatches?: string[]
  channelMatches?: string[]
}

export function nl2rule(input: string) {
  return http.post<Nl2RuleResult>('/ai/nl2rule', { input })
}

export interface ThresholdRecommendation {
  label: string
  value: number
  explain: string
}

export interface ThresholdResponse {
  metricCode: string
  metricName?: string
  unit?: string
  samples: number
  source?: 'HISTORY' | 'EMPIRICAL'
  p50?: number
  p95?: number
  p99?: number
  max?: number
  recommendations: ThresholdRecommendation[]
}

export function recommendThreshold(payload: { objectId?: number; objectType: string; metricCode: string }) {
  return http.post<ThresholdResponse>('/ai/threshold-recommend', payload)
}
