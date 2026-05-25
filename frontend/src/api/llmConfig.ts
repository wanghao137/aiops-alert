import { http } from './http'

export interface LlmModelConfigItem {
  id?: number
  configCode?: string
  configName: string
  provider: string
  baseUrl: string
  apiKey?: string
  apiKeyMasked?: string
  modelName: string
  temperature?: number
  maxTokens?: number
  isDefault?: boolean
  status?: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export function listLlmConfigs() {
  return http.get<LlmModelConfigItem[]>('/llm-configs')
}

export function saveLlmConfig(payload: LlmModelConfigItem) {
  return http.post<LlmModelConfigItem>('/llm-configs', payload)
}

export function deleteLlmConfig(id: number) {
  return http.delete<void>(`/llm-configs/${id}`)
}

export function setDefaultLlmConfig(id: number) {
  return http.post<LlmModelConfigItem>(`/llm-configs/${id}/default`)
}

export interface LlmTestResult {
  success: boolean
  reply?: string
  error?: string
  durationMs?: number
  modelName?: string
}

export function testLlmConfig(id: number) {
  return http.post<LlmTestResult>(`/llm-configs/${id}/test`)
}
