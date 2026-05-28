const DEFAULT_API_BASE_URL = '/api'

export function normalizeApiBaseUrl(raw?: string) {
  const value = (raw || '').trim()
  if (!value) return DEFAULT_API_BASE_URL
  return value.replace(/\/+$/, '') || DEFAULT_API_BASE_URL
}

export const API_BASE_URL = normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL)

export function apiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}
