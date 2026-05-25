import { http } from './http'

export interface ThresholdRecoItem {
  label: string
  value: number | string
  explain: string
}

export interface ThresholdReco {
  metricCode: string
  metricName?: string
  unit?: string
  samples: number
  source?: 'HISTORY' | 'EMPIRICAL'
  p50?: number
  p95?: number
  p99?: number
  max?: number
  recommendations: ThresholdRecoItem[]
}

export function recommendThreshold(query: { objectId?: number; objectType: string; metricCode: string }) {
  return http.get<ThresholdReco>('/ai/threshold', { params: query })
}
