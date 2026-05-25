import { Bell, Activity, AlertTriangle, Flame } from 'lucide-vue-next'
import type { Component } from 'vue'

export interface AlertLevelMeta {
  value: string
  label: string
  color: string
  bg: string
  icon: Component
}

export const ALERT_LEVELS: AlertLevelMeta[] = [
  { value: 'NOTICE',   label: '提示', color: '#0EA5E9', bg: 'rgba(14,165,233,0.12)', icon: Bell },
  { value: 'NORMAL',   label: '一般', color: '#3B82F6', bg: 'rgba(59,130,246,0.12)', icon: Activity },
  { value: 'SERIOUS',  label: '严重', color: '#F59E0B', bg: 'rgba(245,158,11,0.12)', icon: AlertTriangle },
  { value: 'CRITICAL', label: '紧急', color: '#EF4444', bg: 'rgba(239,68,68,0.12)',  icon: Flame }
]

export function getAlertLevelMeta(level?: string): AlertLevelMeta {
  return ALERT_LEVELS.find((l) => l.value === level) || ALERT_LEVELS[1]
}
