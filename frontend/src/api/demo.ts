import { http } from './http'

export function seedDemoData() {
  return http.post<string>('/demo/seed')
}

export function cleanDemoData() {
  return http.post<string>('/demo/clean')
}
