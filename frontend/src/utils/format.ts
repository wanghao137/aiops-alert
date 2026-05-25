/**
 * 友好时间格式化：相对时间 + 绝对时间。
 * "刚刚" / "5 分钟前" / "2 小时前" / "昨天 09:30" / "MM-DD HH:mm"
 */
export function relativeTime(value?: string): string {
  if (!value) return '-'
  const now = Date.now()
  const t = parseTime(value)
  if (!t) return value
  const diffMs = now - t
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr} 小时前`
  const date = new Date(t)
  const today = new Date()
  if (sameDay(date, daysAgo(today, 1))) {
    return `昨天 ${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function fullTime(value?: string): string {
  if (!value) return '-'
  const t = parseTime(value)
  if (!t) return value
  const d = new Date(t)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function parseTime(value: string): number | null {
  // 后端 Jackson 输出 yyyy-MM-dd HH:mm:ss（GMT+8）
  if (value.includes(' ')) {
    const iso = value.replace(' ', 'T') + '+08:00'
    const t = Date.parse(iso)
    if (!isNaN(t)) return t
  }
  const t = Date.parse(value)
  return isNaN(t) ? null : t
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
}

function daysAgo(d: Date, n: number) {
  const r = new Date(d)
  r.setDate(r.getDate() - n)
  return r
}
