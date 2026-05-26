import { http } from './http'

export interface DailyBriefSnapshot {
  totalEvents: number
  criticalEvents: number
  pendingEvents: number
  recoveredEvents: number
  openIncidents: number
  notifyFailed: number
  dayOverDay: number
}

export interface DailyBriefHighlight {
  id: number
  eventNo: string
  objectName: string
  alertLevel: string
  eventTitle: string
  eventStatus: string
  triggeredAt?: string
}

export interface DailyBrief {
  generatedAt: string
  coverageDate: string
  narrative?: string
  /** SUCCESS / FALLBACK / FAILED */
  status: string
  snapshot: DailyBriefSnapshot
  highlights: DailyBriefHighlight[]
}

export function getDailyBrief() {
  return http.get<DailyBrief>('/daily-brief')
}

export function refreshDailyBrief() {
  return http.post<DailyBrief>('/daily-brief/refresh')
}
