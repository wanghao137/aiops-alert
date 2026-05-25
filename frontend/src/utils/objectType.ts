import { Server, Database, ArrowRightLeft, Cog } from 'lucide-vue-next'
import type { Component } from 'vue'

export interface ObjectTypeMeta {
  value: string
  label: string
  short: string
  /** 卡片渐变 */
  gradient: string
  /** 图标主色 */
  color: string
  icon: Component
}

export const OBJECT_TYPES: ObjectTypeMeta[] = [
  {
    value: 'SERVER',
    label: '服务器',
    short: '服务器',
    gradient: 'linear-gradient(135deg, rgba(59,130,246,0.25), rgba(59,130,246,0.05))',
    color: '#3B82F6',
    icon: Server
  },
  {
    value: 'DATABASE',
    label: '数据库',
    short: '数据库',
    gradient: 'linear-gradient(135deg, rgba(139,92,246,0.25), rgba(139,92,246,0.05))',
    color: '#8B5CF6',
    icon: Database
  },
  {
    value: 'SYNC_JOB',
    label: '数据同步作业',
    short: '同步作业',
    gradient: 'linear-gradient(135deg, rgba(16,185,129,0.25), rgba(16,185,129,0.05))',
    color: '#10B981',
    icon: ArrowRightLeft
  },
  {
    value: 'PROCESS_JOB',
    label: '数据加工作业',
    short: '加工作业',
    gradient: 'linear-gradient(135deg, rgba(245,158,11,0.25), rgba(245,158,11,0.05))',
    color: '#F59E0B',
    icon: Cog
  }
]

export function getObjectTypeMeta(type?: string): ObjectTypeMeta {
  return OBJECT_TYPES.find((t) => t.value === type) || OBJECT_TYPES[0]
}
