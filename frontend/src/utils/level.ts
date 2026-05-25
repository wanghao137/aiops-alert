export interface LevelMeta {
  value: string
  label: string
  color: string
  bg: string
  border: string
  text: string
}

export const LEVEL_META: Record<string, LevelMeta> = {
  NOTICE: {
    value: 'NOTICE',
    label: '提示',
    color: '#0EA5E9',
    bg: 'rgba(14, 165, 233, 0.12)',
    border: 'rgba(14, 165, 233, 0.35)',
    text: '#7DD3FC'
  },
  NORMAL: {
    value: 'NORMAL',
    label: '一般',
    color: '#3B82F6',
    bg: 'rgba(59, 130, 246, 0.12)',
    border: 'rgba(59, 130, 246, 0.35)',
    text: '#93C5FD'
  },
  SERIOUS: {
    value: 'SERIOUS',
    label: '严重',
    color: '#F59E0B',
    bg: 'rgba(245, 158, 11, 0.12)',
    border: 'rgba(245, 158, 11, 0.35)',
    text: '#FCD34D'
  },
  CRITICAL: {
    value: 'CRITICAL',
    label: '紧急',
    color: '#EF4444',
    bg: 'rgba(239, 68, 68, 0.14)',
    border: 'rgba(239, 68, 68, 0.4)',
    text: '#FCA5A5'
  }
}

export function levelMeta(level?: string): LevelMeta {
  return LEVEL_META[level || ''] || LEVEL_META.NORMAL
}

export function levelLabel(level?: string) {
  return levelMeta(level).label
}

export const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: '#EF4444' },
  CONFIRMED: { label: '已确认', color: '#F59E0B' },
  RECOVERED: { label: '已恢复', color: '#10B981' },
  CLOSED: { label: '已关闭', color: '#94A3B8' }
}

export function statusLabel(status?: string) {
  return STATUS_META[status || '']?.label || status || ''
}

export function statusColor(status?: string) {
  return STATUS_META[status || '']?.color || '#94A3B8'
}
