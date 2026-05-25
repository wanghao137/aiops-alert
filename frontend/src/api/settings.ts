import { http } from './http'

export interface LlmConfigItem {
  id?: number
  configCode?: string
  configName: string
  provider?: string
  baseUrl: string
  apiKey: string
  modelName: string
  temperature?: number
  maxTokens?: number
  isDefault?: number
  status?: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export function listLlmConfigs() {
  return http.get<LlmConfigItem[]>('/llm-configs')
}

export function saveLlmConfig(payload: LlmConfigItem) {
  return http.post<LlmConfigItem>('/llm-configs', payload)
}

export function setDefaultLlmConfig(id: number) {
  return http.post<void>(`/llm-configs/${id}/set-default`)
}

export function deleteLlmConfig(id: number) {
  return http.delete<void>(`/llm-configs/${id}`)
}
