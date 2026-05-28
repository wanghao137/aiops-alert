import { http } from './http'

export interface MonitorObjectItem {
  id?: number
  objectCode?: string
  objectName: string
  objectType: string
  objectTypeName?: string
  ownerName?: string
  ownerPhone?: string
  tags?: string
  status?: string
  description?: string
  extConfig?: string
  createdAt?: string
  updatedAt?: string
}

export interface MonitorObjectStatsTypeItem {
  objectType: string
  objectTypeName: string
  total: number
  enabled: number
}

export interface MonitorObjectStats {
  total: number
  enabled: number
  byType: MonitorObjectStatsTypeItem[]
}

export interface MonitorObjectQuery {
  objectType?: string
  keyword?: string
  status?: string
}

export interface MonitorObjectDraftResponse {
  draft: MonitorObjectItem
  understanding?: string
  warnings?: string[]
  durationMs?: number
  modelName?: string
  reasoning?: string
}

export function listMonitorObjects(query: MonitorObjectQuery = {}) {
  return http.get<MonitorObjectItem[]>('/monitor-objects', { params: query })
}

export function getMonitorObject(id: number) {
  return http.get<MonitorObjectItem>(`/monitor-objects/${id}`)
}

export function saveMonitorObject(payload: MonitorObjectItem) {
  return http.post<MonitorObjectItem>('/monitor-objects', payload)
}

export function toggleMonitorObject(id: number) {
  return http.post<MonitorObjectItem>(`/monitor-objects/${id}/toggle`)
}

export function deleteMonitorObject(id: number) {
  return http.delete<void>(`/monitor-objects/${id}`)
}

export function getMonitorObjectStats() {
  return http.get<MonitorObjectStats>('/monitor-objects/stats')
}

export function checkMonitorObjectAiAvailability() {
  return http.get<boolean>('/ai/objects/availability')
}

export function draftMonitorObject(prompt: string) {
  return http.post<MonitorObjectDraftResponse>('/ai/objects/draft', { prompt })
}
