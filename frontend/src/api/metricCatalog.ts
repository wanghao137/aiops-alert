import { http } from './http'

export interface MetricEnumOption {
  value: string
  label: string
}

export interface MetricItem {
  code: string
  name: string
  /** numeric / state */
  valueType: string
  unit?: string
  min?: number | null
  max?: number | null
  defaultCompareOp?: string
  defaultThreshold?: string
  options?: MetricEnumOption[]
}

export interface CompareOpItem {
  code: string
  label: string
  symbol: string
  /** numeric / state / any */
  inputKind: string
}

export interface ObjectTypeOption {
  value: string
  label: string
}

export interface AlertLevelOption {
  value: string
  label: string
  /** sky / blue / amber / red */
  tone: string
}

export interface MetricCatalog {
  metricsByType: Record<string, MetricItem[]>
  compareOps: CompareOpItem[]
  objectTypes: ObjectTypeOption[]
  alertLevels: AlertLevelOption[]
}

export function getMetricCatalog() {
  return http.get<MetricCatalog>('/metric-catalog')
}
