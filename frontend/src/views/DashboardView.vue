<template>
  <div class="dashboard-v" v-loading="loading">
    <!-- ========== HERO 区：编辑式大数字 + 状态摘要 ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">SITUATIONAL OVERVIEW</span>
          <span class="dot-anim" />
          <span class="hero-time">{{ now }}</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num" :class="{ urgent: pendingActive }">{{ formatNum(data?.pendingEventTotal) }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ pendingActive ? '正在处理' : '系统平稳' }}</div>
            <div class="hero-line-2">
              {{ pendingActive ? `待处理告警 · 紧急 ${data?.criticalEventTotal || 0} · 严重 ${data?.seriousEventTotal || 0}` : '近期没有未处理事件' }}
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <div class="vital">
          <span class="vital-label">实时连接</span>
          <span class="vital-val">
            <span class="dot live" /> SSE
          </span>
        </div>
        <div class="vital">
          <span class="vital-label">活跃 Incident</span>
          <span class="vital-val tabular-nums">{{ formatNum(data?.openIncidentTotal) }}</span>
        </div>
        <div class="vital">
          <span class="vital-label">今日通知失败</span>
          <span class="vital-val tabular-nums" :class="{ warn: (data?.notifyFailedToday || 0) > 0 }">
            {{ formatNum(data?.notifyFailedToday) }}
          </span>
        </div>
        <button class="noc-btn" @click="toggleNoc" :title="nocActive ? '退出全屏 (Esc)' : '全屏大屏模式 · 30s 轮播刷新'">
          <Tv :size="13" :stroke-width="1.6" />
          {{ nocActive ? '退出大屏' : '大屏模式' }}
        </button>
      </div>
    </section>

    <!-- ========== AI 每日态势简报 ========== -->
    <DailyBriefCard />

    <!-- ========== KPI 双列 ========== -->
    <section class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-eyebrow"><span class="dot" />事件总数</div>
        <div class="kpi-value tabular-nums">{{ formatNum(data?.eventTotal) }}</div>
        <div class="kpi-foot">累计接入</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-eyebrow"><span class="dot warn" />紧急</div>
        <div class="kpi-value tabular-nums">{{ formatNum(data?.criticalEventTotal) }}</div>
        <div class="kpi-foot">CRITICAL 级别</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-eyebrow"><span class="dot ok" />已恢复</div>
        <div class="kpi-value tabular-nums">{{ formatNum(recoveredTotal) }}</div>
        <div class="kpi-foot">流程已闭环</div>
      </div>
      <div class="kpi-card resource">
        <div class="kpi-eyebrow"><span class="dot accent" />资源监控</div>
        <div class="resource-grid">
          <div>
            <span>对象</span>
            <strong class="tabular-nums">{{ data?.enabledObjectTotal || 0 }}<em>/{{ data?.objectTotal || 0 }}</em></strong>
          </div>
          <div>
            <span>规则</span>
            <strong class="tabular-nums">{{ data?.enabledRuleTotal || 0 }}<em>/{{ data?.ruleTotal || 0 }}</em></strong>
          </div>
          <div>
            <span>渠道</span>
            <strong class="tabular-nums">{{ data?.enabledChannelTotal || 0 }}<em>/{{ data?.channelTotal || 0 }}</em></strong>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 主图表区 ========== -->
    <section class="main-grid">
      <!-- 7 日趋势 -->
      <div class="panel-block trend">
        <div class="panel-title-row">
          <div>
            <div class="eyebrow">7-DAY TIMELINE</div>
            <h3 class="panel-h">告警事件时序</h3>
          </div>
          <div class="legend">
            <span><i style="background: var(--accent)" />总量</span>
            <span><i style="background: var(--warn)" />待处理</span>
            <span><i style="background: var(--ok)" />已恢复</span>
            <span><i style="background: var(--critical)" />紧急</span>
          </div>
        </div>
        <div ref="trendRef" class="chart"></div>
      </div>

      <!-- 状态分布 ring -->
      <div class="panel-block">
        <div>
          <div class="eyebrow">STATUS</div>
          <h3 class="panel-h">事件状态分布</h3>
        </div>
        <div ref="statusRef" class="chart small"></div>
      </div>

      <!-- 级别分布 ring -->
      <div class="panel-block">
        <div>
          <div class="eyebrow">SEVERITY</div>
          <h3 class="panel-h">告警级别分布</h3>
        </div>
        <div ref="levelRef" class="chart small"></div>
      </div>

      <!-- 规则命中 Top -->
      <div class="panel-block hits">
        <div class="panel-title-row">
          <div>
            <div class="eyebrow">RULE HIT TOP</div>
            <h3 class="panel-h">命中规则排行 · 近 7 天</h3>
          </div>
        </div>
        <div class="hit-list">
          <div v-for="(it, i) in data?.ruleHitTop || []" :key="`${it.ruleId}-${i}`" class="hit-row">
            <span class="rank tabular-nums">{{ String(i + 1).padStart(2, '0') }}</span>
            <div class="hit-meta">
              <strong>{{ it.ruleName }}</strong>
              <small>{{ objectTypeName(it.objectType) }}</small>
            </div>
            <div class="hit-bar">
              <div class="bar-fill" :style="{ width: barWidth(it.hitCount) }" />
            </div>
            <b class="tabular-nums">{{ it.hitCount }}</b>
          </div>
          <div v-if="!data?.ruleHitTop?.length" class="empty-mini">
            近 7 天尚无规则命中
          </div>
        </div>
      </div>

      <!-- 对象类型分布 -->
      <div class="panel-block">
        <div>
          <div class="eyebrow">SOURCE</div>
          <h3 class="panel-h">告警来源分布</h3>
        </div>
        <div class="dist-list">
          <div
            v-for="d in data?.objectTypeDistribution || []"
            :key="d.code"
            class="dist-row"
          >
            <component :is="getObjectTypeMeta(d.code).icon" :size="13" :stroke-width="1.6"
              :style="{ color: getObjectTypeMeta(d.code).color }" />
            <span>{{ d.name }}</span>
            <div class="dist-bar">
              <div class="dist-fill" :style="{
                width: `${maxObjectType > 0 ? (d.value / maxObjectType) * 100 : 0}%`,
                background: getObjectTypeMeta(d.code).color
              }" />
            </div>
            <strong class="tabular-nums">{{ d.value }}</strong>
          </div>
        </div>
      </div>

      <!-- 最近事件 feed -->
      <div class="panel-block recent">
        <div class="panel-title-row">
          <div>
            <div class="eyebrow">RECENT FEED</div>
            <h3 class="panel-h">最近事件</h3>
          </div>
          <button class="link-more" @click="$router.push('/events')">
            查看全部 <ArrowRight :size="12" :stroke-width="1.8" />
          </button>
        </div>
        <div class="feed">
          <div
            v-for="ev in data?.recentEvents || []"
            :key="ev.id"
            class="feed-item"
            @click="$router.push('/events')"
          >
            <span class="feed-time tabular-nums">{{ formatTime(ev.lastTriggeredAt) }}</span>
            <span class="feed-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
            <div class="feed-body">
              <div class="feed-line-1">
                <span class="feed-title">{{ ev.eventTitle }}</span>
                <span class="lv-tag" :style="{
                  color: getAlertLevelMeta(ev.alertLevel).color,
                  borderColor: getAlertLevelMeta(ev.alertLevel).color
                }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
              </div>
              <div class="feed-line-2">
                <span>{{ ev.objectName }}</span>
                <span class="sep">·</span>
                <span>{{ ev.metricName }}</span>
                <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
              </div>
            </div>
          </div>
          <div v-if="!data?.recentEvents?.length" class="empty-mini">暂无事件</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { ArrowRight, Tv } from 'lucide-vue-next'
import { getObjectTypeMeta } from '@/utils/objectType'
import { getAlertLevelMeta } from '@/utils/alertLevel'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import { useRealtimeStore } from '@/stores/realtime'
import { useThemeStore } from '@/stores/theme'
import DailyBriefCard from '@/components/common/DailyBriefCard.vue'

const data = ref<DashboardData>()
const loading = ref(false)
const trendRef = ref<HTMLDivElement>()
const statusRef = ref<HTMLDivElement>()
const levelRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | undefined
let statusChart: echarts.ECharts | undefined
let levelChart: echarts.ECharts | undefined

const realtime = useRealtimeStore()
const theme = useThemeStore()

/* 从 :root 上的 CSS 变量读 token，echarts 颜色随主题切换 */
function readToken(name: string, fallback: string) {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

// 实时时钟
const now = ref(formatNow())
let timer: number | undefined
function formatNow() {
  const d = new Date()
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

const recoveredTotal = computed(() => {
  const dist = data.value?.statusDistribution || []
  return dist.find((d) => d.code === 'RECOVERED')?.value || 0
})

const pendingActive = computed(() => (data.value?.pendingEventTotal || 0) > 0)

const maxHit = computed(() => {
  return (data.value?.ruleHitTop || []).reduce((m, x) => Math.max(m, x.hitCount), 0) || 1
})

const maxObjectType = computed(() => {
  return (data.value?.objectTypeDistribution || []).reduce((m, x) => Math.max(m, x.value), 0) || 1
})

function barWidth(v: number) {
  return `${Math.max(2, (v / maxHit.value) * 100)}%`
}

async function loadAll() {
  loading.value = true
  try {
    data.value = await getDashboard()
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTrend()
  renderRing(statusRef.value, 'status', data.value?.statusDistribution || [])
  renderRing(levelRef.value, 'level', data.value?.levelDistribution || [])
}

function renderTrend() {
  if (!trendRef.value || !data.value) return
  trendChart = trendChart || echarts.init(trendRef.value)
  const trend = data.value.sevenDayTrend || []

  const bgElev1 = readToken('--bg-elev-1', '#0E1018')
  const lineStrong = readToken('--line-strong', '#2A2F3F')
  const lineCol = readToken('--line', '#1C2030')
  const textPrimary = readToken('--text-primary', '#F4F5FB')
  const textMuted = readToken('--text-muted', '#6E7385')

  const accent = readToken('--accent', '#7DD3FC')
  const warn = readToken('--warn', '#FBBF24')
  const ok = readToken('--ok', '#34D399')
  const critical = readToken('--critical', '#FB7185')

  trendChart.setOption({
    backgroundColor: 'transparent',
    color: [accent, warn, ok, critical],
    tooltip: {
      trigger: 'axis',
      backgroundColor: bgElev1,
      borderColor: lineStrong,
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: textPrimary, fontFamily: 'JetBrains Mono', fontSize: 11 }
    },
    grid: { left: 36, right: 18, top: 18, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date.slice(5)),
      boundaryGap: false,
      axisLine: { lineStyle: { color: lineStrong } },
      axisTick: { show: false },
      axisLabel: { color: textMuted, fontFamily: 'JetBrains Mono', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: lineCol, type: 'dashed' } },
      axisLabel: { color: textMuted, fontFamily: 'JetBrains Mono', fontSize: 10 }
    },
    series: [
      {
        name: '总量', type: 'line', smooth: 0.4, symbol: 'circle', symbolSize: 6,
        lineStyle: { width: 1.6 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: hexToRgba(accent, 0.25) },
            { offset: 1, color: hexToRgba(accent, 0) }
          ])
        },
        data: trend.map((t) => t.total)
      },
      { name: '待处理', type: 'line', smooth: 0.4, symbol: 'circle', symbolSize: 5, lineStyle: { width: 1.4 }, data: trend.map((t) => t.pending) },
      { name: '已恢复', type: 'line', smooth: 0.4, symbol: 'circle', symbolSize: 5, lineStyle: { width: 1.4 }, data: trend.map((t) => t.recovered) },
      { name: '紧急', type: 'line', smooth: 0.4, symbol: 'circle', symbolSize: 5, lineStyle: { width: 1.4 }, data: trend.map((t) => t.critical) }
    ]
  })
}

function hexToRgba(hex: string, alpha: number) {
  const m = hex.replace('#', '').match(/^([0-9a-f]{6})$/i)
  if (!m) return hex
  const r = parseInt(m[1].slice(0, 2), 16)
  const g = parseInt(m[1].slice(2, 4), 16)
  const b = parseInt(m[1].slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function renderRing(el: HTMLDivElement | undefined, kind: 'status' | 'level',
                    list: { code: string; name: string; value: number }[]) {
  if (!el) return
  const chart = kind === 'status'
    ? (statusChart = statusChart || echarts.init(el))
    : (levelChart = levelChart || echarts.init(el))

  const bgElev1 = readToken('--bg-elev-1', '#0E1018')
  const lineStrong = readToken('--line-strong', '#2A2F3F')
  const textPrimary = readToken('--text-primary', '#F4F5FB')
  const textSecondary = readToken('--text-secondary', '#B5B9CC')
  const textMuted = readToken('--text-muted', '#6E7385')

  const accent = readToken('--accent', '#7DD3FC')
  const warn = readToken('--warn', '#FBBF24')
  const ok = readToken('--ok', '#34D399')
  const critical = readToken('--critical', '#FB7185')
  // 用 accent 衍生一个紫色系替代色，避免硬编码
  const accentAlt = readToken('--accent', '#7DD3FC')

  const colors = kind === 'level'
    ? [accent, accentAlt, warn, critical]
    : [warn, accent, ok, textMuted]
  const total = list.reduce((s, x) => s + x.value, 0)
  chart.setOption({
    backgroundColor: 'transparent',
    color: colors,
    tooltip: {
      trigger: 'item',
      backgroundColor: bgElev1,
      borderColor: lineStrong,
      borderWidth: 1,
      textStyle: { color: textPrimary, fontFamily: 'JetBrains Mono', fontSize: 11 },
      formatter: (p: { name: string; value: number; percent: number }) =>
        `${p.name} <b style="color:${textPrimary}">${p.value}</b>　${p.percent}%`
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      icon: 'circle',
      itemWidth: 6,
      itemHeight: 6,
      itemGap: 8,
      textStyle: { color: textSecondary, fontSize: 11, fontFamily: 'Noto Sans SC' },
      formatter: (name: string) => {
        const it = list.find((x) => x.name === name)
        return `${name}  ${it?.value || 0}`
      }
    },
    series: [{
      type: 'pie',
      radius: ['58%', '78%'],
      center: ['32%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderColor: bgElev1, borderWidth: 2 },
      label: {
        show: true,
        position: 'center',
        formatter: () => `{n|${total}}\n{l|TOTAL}`,
        rich: {
          n: { fontFamily: 'Space Grotesk', fontSize: 22, fontWeight: 500, color: textPrimary, lineHeight: 24 },
          l: { fontFamily: 'JetBrains Mono', fontSize: 9, color: textMuted, letterSpacing: 2 }
        }
      },
      labelLine: { show: false },
      data: list.map((d) => ({ name: d.name, value: d.value }))
    }]
  })
}

function resize() {
  trendChart?.resize()
  statusChart?.resize()
  levelChart?.resize()
}

function objectTypeName(t?: string) {
  return getObjectTypeMeta(t).label
}

function formatTime(t?: string) {
  return t ? dayjs(t).format('HH:mm:ss') : '--:--:--'
}

function formatNum(v?: number | null) {
  if (v == null) return '0'
  return v.toLocaleString('en-US')
}

watch(() => realtime.lastEventId, () => loadAll())

/* 主题切换时重渲染 echarts，让颜色与新 token 一致 */
watch(() => theme.isDark, () => {
  requestAnimationFrame(() => {
    if (data.value) renderCharts()
  })
})

// ============================================================
// NOC 全屏大屏模式（P2-11）
// ============================================================
const nocActive = ref(false)
let nocRefreshTimer: number | undefined
let nocRotateTimer: number | undefined

function enterNoc() {
  nocActive.value = true
  document.documentElement.dataset.noc = '1'
  // 浏览器全屏（用户手势中触发即可，不强制）
  if (document.documentElement.requestFullscreen) {
    document.documentElement.requestFullscreen().catch(() => { /* 用户拒绝时忽略 */ })
  }
  // 每 30s 自动 reload dashboard 数据
  nocRefreshTimer = window.setInterval(() => {
    loadAll()
  }, 30_000)
}

function exitNoc() {
  nocActive.value = false
  delete document.documentElement.dataset.noc
  if (document.fullscreenElement && document.exitFullscreen) {
    document.exitFullscreen().catch(() => { /* ignore */ })
  }
  if (nocRefreshTimer) {
    clearInterval(nocRefreshTimer)
    nocRefreshTimer = undefined
  }
  if (nocRotateTimer) {
    clearInterval(nocRotateTimer)
    nocRotateTimer = undefined
  }
}

function toggleNoc() {
  if (nocActive.value) exitNoc()
  else enterNoc()
}

function onKeyDown(e: KeyboardEvent) {
  if (nocActive.value && e.key === 'Escape') {
    exitNoc()
  }
}

function onFullscreenChange() {
  // 用户用 F11 / 浏览器按钮退出时同步状态
  if (!document.fullscreenElement && nocActive.value) {
    exitNoc()
  }
}

onMounted(() => {
  loadAll()
  timer = window.setInterval(() => { now.value = formatNow() }, 30_000)
  window.addEventListener('resize', resize)
  window.addEventListener('keydown', onKeyDown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', resize)
  window.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (nocRefreshTimer) clearInterval(nocRefreshTimer)
  if (nocRotateTimer) clearInterval(nocRotateTimer)
  // 离开 dashboard 时如果还在 NOC 模式，清理 html dataset，避免影响其它页面
  delete document.documentElement.dataset.noc
  trendChart?.dispose()
  statusChart?.dispose()
  levelChart?.dispose()
})
</script>

<style scoped>
.dashboard-v {
  display: grid;
  gap: 22px;
  padding: 0 28px 32px;
  animation: fade-up 0.35s ease both;
}

/* ========== HERO ========== */
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding: 36px 0 28px;
  border-bottom: 1px solid var(--line);
  position: relative;
}

.hero::before {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 80px;
  height: 1px;
  background: var(--accent);
}

.hero-left { min-width: 0; }

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.hero-eyebrow .dot-anim {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ok);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.hero-time {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.hero-headline {
  display: flex;
  align-items: flex-end;
  gap: 28px;
}

.hero-num {
  font-size: 96px;
  letter-spacing: -0.05em;
  color: var(--text-primary);
  line-height: 0.85;
}

.hero-num.urgent { color: var(--accent); }

.hero-words {
  padding-bottom: 8px;
}

.hero-line-1 {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.hero-line-2 {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

.hero-right {
  display: flex;
  gap: 36px;
}

.vital {
  display: grid;
  gap: 8px;
  text-align: right;
}

.vital-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.vital-val {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.01em;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.vital-val.warn { color: var(--warn); }

.vital-val .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ok);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

/* NOC 大屏模式按钮（P2-11） */
.noc-btn {
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.noc-btn:hover {
  background: var(--accent);
  color: var(--bg-base);
}

/* ========== KPI ========== */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.kpi-card {
  position: relative;
  display: grid;
  gap: 14px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
  transition: all 0.15s ease;
}

.kpi-card:hover {
  border-color: var(--line-strong);
  background: var(--bg-elev-2);
}

.kpi-card::before {
  content: '';
  position: absolute;
  top: 0; right: 0;
  width: 60px; height: 60px;
  background: radial-gradient(circle, var(--accent-soft), transparent 70%);
  pointer-events: none;
  opacity: 0.5;
}

.kpi-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.kpi-eyebrow .dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--text-muted);
}
.kpi-eyebrow .dot.warn { background: var(--warn); }
.kpi-eyebrow .dot.ok { background: var(--ok); }
.kpi-eyebrow .dot.accent { background: var(--accent); }

.kpi-value {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 38px;
  letter-spacing: -0.04em;
  color: var(--text-primary);
  line-height: 1;
}

.kpi-foot {
  font-size: 12px;
  color: var(--text-muted);
}

.kpi-card.resource .resource-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.kpi-card.resource .resource-grid > div {
  display: grid;
  gap: 4px;
}

.kpi-card.resource .resource-grid span {
  font-size: 11px;
  color: var(--text-muted);
}

.kpi-card.resource .resource-grid strong {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 500;
  color: var(--text-primary);
}

.kpi-card.resource .resource-grid em {
  font-style: normal;
  color: var(--text-faint);
  font-size: 13px;
  margin-left: 1px;
}

@media (max-width: 1280px) {
  .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .hero-right { display: none; }
}

/* ========== Main grid ========== */
.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.panel-block {
  position: relative;
  display: grid;
  gap: 14px;
  padding: 20px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  align-content: start;
}

.panel-block.trend { grid-column: span 2; grid-row: span 1; }
.panel-block.hits { grid-column: span 2; }
.panel-block.recent { grid-column: span 3; }

@media (max-width: 1280px) {
  .main-grid { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  .panel-block.trend, .panel-block.hits, .panel-block.recent { grid-column: span 2; }
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.panel-h {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.legend {
  display: flex;
  gap: 14px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-muted);
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.legend i {
  width: 10px;
  height: 2px;
  border-radius: 1px;
  display: inline-block;
}

.chart {
  width: 100%;
  height: 240px;
  margin-top: 6px;
}

.chart.small { height: 220px; }

/* hit list */
.hit-list { display: grid; gap: 4px; }

.hit-row {
  display: grid;
  grid-template-columns: 28px 1fr 110px 36px;
  gap: 12px;
  align-items: center;
  padding: 10px 8px;
  border-radius: var(--radius-sm);
  transition: background 0.12s ease;
}

.hit-row:hover {
  background: var(--bg-elev-2);
}

.rank {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.05em;
}

.hit-meta { display: grid; gap: 2px; min-width: 0; }
.hit-meta strong {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hit-meta small {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.hit-bar {
  height: 4px;
  background: var(--bg-elev-3);
  border-radius: 2px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), rgba(125, 211, 252, 0.4));
  border-radius: 2px;
}

.hit-row b {
  text-align: right;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

/* dist list */
.dist-list { display: grid; gap: 8px; }

.dist-row {
  display: grid;
  grid-template-columns: 14px 1fr 60px 30px;
  gap: 10px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed var(--line);
}

.dist-row:last-child { border-bottom: 0; }

.dist-row span {
  font-size: 12px;
  color: var(--text-secondary);
}

.dist-row strong {
  text-align: right;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}

.dist-bar {
  height: 4px;
  background: var(--bg-elev-3);
  border-radius: 2px;
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  border-radius: 2px;
}

/* recent feed */
.feed { display: grid; gap: 0; }

.feed-item {
  display: grid;
  grid-template-columns: 80px 3px 1fr;
  gap: 14px;
  padding: 12px 0;
  border-top: 1px dashed var(--line);
  cursor: pointer;
  transition: background 0.12s ease;
  align-items: center;
}

.feed-item:first-child { border-top: 0; }

.feed-item:hover {
  background: var(--bg-elev-2);
  margin: 0 -10px;
  padding-left: 10px;
  padding-right: 10px;
  border-radius: var(--radius-sm);
}

.feed-time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.feed-bar {
  width: 2px;
  height: 24px;
  border-radius: 2px;
}

.feed-body { min-width: 0; display: grid; gap: 4px; }

.feed-line-1 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.feed-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lv-tag {
  padding: 1px 7px;
  border: 1px solid;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.feed-line-2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.feed-line-2 .sep { color: var(--text-faint); }

.feed-line-2 code {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-elev-3);
  color: var(--text-secondary);
  font-size: 10px;
}

.empty-mini {
  padding: 28px 12px;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}

.link-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  cursor: pointer;
}

.link-more:hover { text-decoration: underline; text-underline-offset: 2px; }
</style>
