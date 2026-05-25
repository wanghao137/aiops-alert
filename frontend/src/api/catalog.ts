import { http } from './http'

export interface DictItem {
  code: string
  name: string
}

export interface CompareOpItem {
  code: string
  label: string
  symbol: string
  needThreshold: boolean
}

export interface MetricItem {
  code: string
  name: string
  unit?: string
  valueType: string
  compareOps: string[]
  enumValues?: string[]
  suggestLevel?: string
  range?: string
}

export function listObjectTypes() {
  return http.get<DictItem[]>('/catalog/object-types')
}

export function listChannelTypes() {
  return http.get<DictItem[]>('/catalog/channel-types')
}

export function listAlertLevels() {
  return http.get<DictItem[]>('/catalog/alert-levels')
}

export function listEventStatuses() {
  return http.get<DictItem[]>('/catalog/event-statuses')
}

export function listCompareOps() {
  return http.get<CompareOpItem[]>('/catalog/compare-ops')
}

export function getMetrics(objectType?: string) {
  return http.get<Record<string, MetricItem[]>>('/catalog/metrics', {
    params: objectType ? { objectType } : {}
  })
}
