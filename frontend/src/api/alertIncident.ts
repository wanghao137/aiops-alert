import { http } from './http'
import type { AlertEventItem } from './alertEvent'

export interface AlertIncidentItem {
  id: number
  incidentNo: string
  objectId: number
  objectType: string
  objectName: string
  topLevel: string
  eventCount: number
  status: string
  summary?: string
  firstEventAt?: string
  lastEventAt?: string
  closedAt?: string
  createdAt?: string
  updatedAt?: string
  events?: AlertEventItem[]
}

export function listIncidents(query: { status?: string; objectType?: string } = {}) {
  return http.get<AlertIncidentItem[]>('/alert-incidents', { params: query })
}

export function getIncident(id: number) {
  return http.get<AlertIncidentItem>(`/alert-incidents/${id}`)
}
