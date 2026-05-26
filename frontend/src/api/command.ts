import { http } from './http'
import type { AlertEventItem } from './alertEvent'

export interface CommandResult {
  intent: string
  answer: string
  routePath?: string
  events?: AlertEventItem[]
  total?: number
  pending?: number
  critical?: number
  rawOutput?: string
  reasoning?: string
  modelName?: string
  durationMs?: number
}

export function runCommand(prompt: string) {
  return http.post<CommandResult>('/ai/command', { prompt })
}
