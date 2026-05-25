import { http } from './http'
import type { AlertRuleItem } from './alertRule'

export interface NlRuleDraftResponse {
  draft: AlertRuleItem
  understanding?: string
  warnings?: string[]
  durationMs?: number
  modelName?: string
}

export function checkNlRuleAvailability() {
  return http.get<boolean>('/ai/rules/availability')
}

export function draftNlRule(prompt: string) {
  return http.post<NlRuleDraftResponse>('/ai/rules/draft', { prompt })
}
