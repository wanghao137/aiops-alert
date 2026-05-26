<template>
  <ErrorPage v-if="error500" variant="500" :on-retry="loadAll" />
  <div v-else class="ai-stats-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">AI ENGINEERING</span>
          <span class="dot-anim" />
          <span class="hero-time">大模型用量 · 成本 · 健康度</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num">{{ overview?.todayCallTotal ?? 0 }}</span>
          <div class="hero-words">
            <div class="hero-line-1">今日 AI 调用</div>
            <div class="hero-line-2">
              token {{ formatNum(overview?.todayTokenTotal) }} ·
              成功率 {{ formatPct(overview?.todaySuccessRate) }} ·
              成本 ¥{{ formatMoney(overview?.todayCost) }}
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <div class="vital">
          <span class="v-label">vs 昨日 调用</span>
          <Delta :today="overview?.todayCallTotal ?? 0" :yesterday="overview?.yesterdayCallTotal ?? 0" />
        </div>
        <div class="vital">
          <span class="v-label">vs 昨日 token</span>
          <Delta :today="overview?.todayTokenTotal ?? 0" :yesterday="overview?.yesterdayTokenTotal ?? 0" />
        </div>
        <div class="vital">
          <span class="v-label">vs 昨日 成功率</span>
          <Delta :today="overview?.todaySuccessRate ?? 0" :yesterday="overview?.yesterdaySuccessRate ?? 0"
            :is-pct="true" />
        </div>
      </div>
    </section>

    <!-- ========== Hero KPI ========== -->
    <section class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-eyebrow"><span class="dot accent" />TOKEN · TODAY</div>
        <div class="kpi-value tabular-nums">{{ formatNum(overview?.todayTokenTotal) }}</div>
        <div class="kpi-foot">prompt + completion</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-eyebrow"><span class="dot ok" />SUCCESS RATE</div>
        <div class="kpi-value tabular-nums">{{ formatPct(overview?.todaySuccessRate) }}</div>
        <div class="kpi-foot">SUCCESS / 全部今日调用</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-eyebrow"><span class="dot warn" />COST · TODAY</div>
        <div class="kpi-value tabular-nums">¥{{ formatMoney(overview?.todayCost) }}</div>
        <div class="kpi-foot">本月累计 ¥{{ formatMoney(overview?.monthCost) }}</div>
      </div>
    </section>

    <!-- ========== 双图表区 ========== -->
    <section class="chart-row">
      <div class="panel-block">
        <div class="panel-title-row">
          <div>
            <div class="eyebrow">SCENE DISTRIBUTION</div>
            <h3 class="panel-h">今日场景分布</h3>
          </div>
          <div class="legend">
            <span v-for="s in overview?.sceneDistribution || []" :key="s.scene">
              <i :style="{ background: sceneColor(s.scene) }" />
              {{ sceneLabel(s.scene) }}
            </span>
          </div>
        </div>
        <div ref="sceneRef" class="chart" />
      </div>

      <div class="panel-block">
        <div class="panel-title-row">
          <div>
            <div class="eyebrow">7-DAY TIMELINE</div>
            <h3 class="panel-h">近 7 天调用趋势</h3>
          </div>
        </div>
        <div ref="trendRef" class="chart" />
      </div>
    </section>

    <!-- ========== 慢调用 Top 10 ========== -->
    <section class="panel-block slow-card">
      <div class="panel-title-row">
        <div>
          <div class="eyebrow">SLOW CALLS · TOP 10 · 7D</div>
          <h3 class="panel-h">慢调用排行</h3>
        </div>
      </div>
      <table class="slow-table">
        <thead>
          <tr>
            <th class="rank">#</th>
            <th>耗时</th>
            <th>场景</th>
            <th>模型</th>
            <th>时间</th>
            <th class="ops"></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="(log, i) in slowList" :key="log.id">
            <tr class="data-row" :class="{ expanded: expandedId === log.id }">
              <td class="rank tabular-nums">{{ String(i + 1).padStart(2, '0') }}</td>
              <td class="dur tabular-nums">{{ log.durationMs }} ms</td>
              <td>
                <span class="scene-tag" :style="{
                  color: sceneColor(log.scene),
                  borderColor: sceneColor(log.scene)
                }">{{ sceneLabel(log.scene) }}</span>
              </td>
              <td class="model">{{ log.modelName || '-' }}</td>
              <td class="time tabular-nums">{{ formatTime(log.createdAt) }}</td>
              <td class="ops">
                <button class="link-btn" @click="toggleExpand(log)">
                  {{ expandedId === log.id ? '收起' : '展开' }}
                  <ChevronDown v-if="expandedId !== log.id" :size="11" :stroke-width="1.8" />
                  <ChevronUp v-else :size="11" :stroke-width="1.8" />
                </button>
              </td>
            </tr>
            <tr v-if="expandedId === log.id" class="expand-row">
              <td colspan="6">
                <div v-if="expandedDetail" class="payload-grid">
                  <div class="payload">
                    <div class="payload-head">REQUEST</div>
                    <pre>{{ expandedDetail.requestPayload || '（无）' }}</pre>
                  </div>
                  <div class="payload">
                    <div class="payload-head">RESPONSE</div>
                    <pre>{{ expandedDetail.responsePayload || '（无）' }}</pre>
                  </div>
                </div>
                <div v-else class="payload-loading">加载中…</div>
              </td>
            </tr>
          </template>
          <tr v-if="!slowLoading && !slowList.length" class="empty-row">
            <td colspan="6">
              <div class="empty-mini">近 7 天暂无慢调用记录</div>
            </td>
          </tr>
          <tr v-if="slowLoading" class="empty-row">
            <td colspan="6">
              <SkeletonList :rows="4" variant="row" />
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- ========== 调用流水 ========== -->
    <section class="panel-block log-list">
      <div class="panel-title-row">
        <div>
          <div class="eyebrow">CALL LOG · {{ logsPage.total }}</div>
          <h3 class="panel-h">AI 调用流水</h3>
        </div>
        <div class="filters">
          <el-select v-model="filters.scene" placeholder="场景" clearable class="f-select" @change="reloadLogs">
            <el-option v-for="s in SCENE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-select v-model="filters.status" placeholder="状态" clearable class="f-select" @change="reloadLogs">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-input v-model="filters.modelName" placeholder="模型名" clearable class="f-input"
            @keyup.enter="reloadLogs" @clear="reloadLogs" />
        </div>
      </div>

      <SkeletonList v-if="logsLoading && !logsPage.records.length" :rows="6" variant="row" />
      <table v-else class="log-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>场景</th>
            <th>模型</th>
            <th>状态</th>
            <th class="num">耗时</th>
            <th class="num">prompt</th>
            <th class="num">completion</th>
            <th class="num">成本</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logsPage.records" :key="log.id" class="data-row" @click="openDetail(log)">
            <td class="time tabular-nums">{{ formatTime(log.createdAt) }}</td>
            <td>
              <span class="scene-tag" :style="{
                color: sceneColor(log.scene),
                borderColor: sceneColor(log.scene)
              }">{{ sceneLabel(log.scene) }}</span>
            </td>
            <td class="model">{{ log.modelName || '-' }}</td>
            <td>
              <span class="st-pill" :class="log.status === 'SUCCESS' ? 'ok' : 'fail'">{{ log.status }}</span>
            </td>
            <td class="num tabular-nums">{{ log.durationMs ?? '-' }}</td>
            <td class="num tabular-nums">{{ log.promptTokens ?? '-' }}</td>
            <td class="num tabular-nums">{{ log.completionTokens ?? '-' }}</td>
            <td class="num tabular-nums">¥{{ formatMoney(log.estimatedCost) }}</td>
          </tr>
          <tr v-if="!logsLoading && !logsPage.records.length" class="empty-row">
            <td colspan="8">
              <div class="empty-mini">没有匹配的调用流水</div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="logsPage.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reloadLogs"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <!-- ========== 详情抽屉 ========== -->
    <el-drawer
      v-model="detailVisible"
      :size="640"
      :with-header="false"
      class="ai-call-drawer"
    >
      <div v-if="detail" class="drawer-content">
        <div class="drawer-head">
          <div>
            <div class="eyebrow">CALL #{{ detail.id }} · {{ detail.scene }}</div>
            <h3 class="drawer-title">{{ detail.modelName || '未知模型' }}</h3>
          </div>
          <button class="close" @click="detailVisible = false">
            <X :size="14" :stroke-width="1.8" />
          </button>
        </div>

        <div class="drawer-meta">
          <div class="meta-cell">
            <span class="lbl">状态</span>
            <span class="val">
              <span class="st-pill" :class="detail.status === 'SUCCESS' ? 'ok' : 'fail'">{{ detail.status }}</span>
            </span>
          </div>
          <div class="meta-cell">
            <span class="lbl">耗时</span>
            <span class="val tabular-nums">{{ detail.durationMs }} ms</span>
          </div>
          <div class="meta-cell">
            <span class="lbl">prompt</span>
            <span class="val tabular-nums">{{ detail.promptTokens ?? '-' }}</span>
          </div>
          <div class="meta-cell">
            <span class="lbl">completion</span>
            <span class="val tabular-nums">{{ detail.completionTokens ?? '-' }}</span>
          </div>
          <div class="meta-cell">
            <span class="lbl">成本</span>
            <span class="val tabular-nums">¥{{ formatMoney(detail.estimatedCost) }}</span>
          </div>
          <div class="meta-cell">
            <span class="lbl">时间</span>
            <span class="val tabular-nums">{{ formatTime(detail.createdAt) }}</span>
          </div>
        </div>

        <div v-if="detail.errorMessage" class="error-block">
          <AlertTriangle :size="13" :stroke-width="1.8" />
          <span>{{ detail.errorMessage }}</span>
        </div>

        <div class="payload-section">
          <div class="payload-head">REQUEST</div>
          <pre>{{ detail.requestPayload || '（无）' }}</pre>
        </div>

        <div class="payload-section">
          <div class="payload-head">RESPONSE</div>
          <pre>{{ detail.responsePayload || '（无）' }}</pre>
        </div>

        <div v-if="detail.reasoningContent" class="payload-section">
          <div class="payload-head">REASONING</div>
          <pre>{{ detail.reasoningContent }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { ChevronDown, ChevronUp, X, AlertTriangle } from 'lucide-vue-next'
import {
  getAiStatsOverview, listSlowAiCalls, listAiCallLogs, getAiCallLog,
  type AiStatsOverview, type AiCallLogItem
} from '@/api/aiStats'
import { useThemeStore } from '@/stores/theme'
import SkeletonList from '@/components/common/SkeletonList.vue'
import ErrorPage from '@/views/ErrorPage.vue'

const SCENE_OPTIONS = [
  { value: 'NL2RULE', label: 'NL2Rule 建规则' },
  { value: 'EVENT_SUMMARY', label: '事件摘要' },
  { value: 'CHAT', label: '命令面板' },
  { value: 'THRESHOLD', label: '阈值推荐' },
  { value: 'OTHER', label: '其他' }
]

function sceneLabel(scene: string) {
  return SCENE_OPTIONS.find((o) => o.value === scene)?.label || scene || '-'
}

function readToken(name: string, fallback: string) {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

function sceneColor(scene: string) {
  const map: Record<string, string> = {
    NL2RULE: readToken('--accent', '#7DD3FC'),
    EVENT_SUMMARY: readToken('--warn', '#FBBF24'),
    CHAT: readToken('--ok', '#34D399'),
    THRESHOLD: readToken('--critical', '#FB7185'),
    OTHER: readToken('--text-muted', '#6E7385')
  }
  return map[scene] || readToken('--text-muted', '#6E7385')
}

const theme = useThemeStore()

// ---------------- 状态 ----------------
const overview = ref<AiStatsOverview | null>(null)
const slowList = ref<AiCallLogItem[]>([])
const slowLoading = ref(false)
const logsLoading = ref(false)
const logsPage = ref<{ total: number; records: AiCallLogItem[] }>({ total: 0, records: [] })
const error500 = ref<{ retry: () => void } | null>(null)

const filters = reactive({
  scene: '',
  modelName: '',
  status: '',
  page: 1,
  size: 20
})

// 慢调用展开
const expandedId = ref<number | null>(null)
const expandedDetail = ref<AiCallLogItem | null>(null)

// 流水详情抽屉
const detailVisible = ref(false)
const detail = ref<AiCallLogItem | null>(null)

// ---------------- echarts ----------------
const sceneRef = ref<HTMLDivElement>()
const trendRef = ref<HTMLDivElement>()
let sceneChart: echarts.ECharts | undefined
let trendChart: echarts.ECharts | undefined

function renderSceneRing() {
  if (!sceneRef.value || !overview.value) return
  if (!sceneChart) sceneChart = echarts.init(sceneRef.value)
  const list = overview.value.sceneDistribution || []
  const total = list.reduce((s, x) => s + x.callCount, 0)
  const bgElev1 = readToken('--bg-elev-1', '#0E1018')
  const lineStrong = readToken('--line-strong', '#2A2F3F')
  const textPrimary = readToken('--text-primary', '#F4F5FB')
  const textSecondary = readToken('--text-secondary', '#B5B9CC')
  const textMuted = readToken('--text-muted', '#6E7385')

  sceneChart.setOption({
    backgroundColor: 'transparent',
    color: list.map((s) => sceneColor(s.scene)),
    tooltip: {
      trigger: 'item',
      backgroundColor: bgElev1,
      borderColor: lineStrong,
      borderWidth: 1,
      textStyle: { color: textPrimary, fontFamily: 'JetBrains Mono', fontSize: 11 },
      formatter: (p: { name: string; value: number; percent: number }) =>
        `${sceneLabel(p.name)} <b style="color:${textPrimary}">${p.value}</b>　${p.percent}%`
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
        const it = list.find((x) => x.scene === name)
        return `${sceneLabel(name)}  ${it?.callCount || 0}`
      },
      data: list.map((s) => s.scene)
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
        formatter: () => `{n|${total}}\n{l|TODAY CALLS}`,
        rich: {
          n: { fontFamily: 'Space Grotesk', fontSize: 22, fontWeight: 500, color: textPrimary, lineHeight: 24 },
          l: { fontFamily: 'JetBrains Mono', fontSize: 9, color: textMuted, letterSpacing: 2 }
        }
      },
      labelLine: { show: false },
      data: list.map((s) => ({ name: s.scene, value: s.callCount }))
    }]
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

function renderTrendLine() {
  if (!trendRef.value || !overview.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const trend = overview.value.sevenDayTrend || []

  const bgElev1 = readToken('--bg-elev-1', '#0E1018')
  const lineStrong = readToken('--line-strong', '#2A2F3F')
  const lineCol = readToken('--line', '#1C2030')
  const textPrimary = readToken('--text-primary', '#F4F5FB')
  const textMuted = readToken('--text-muted', '#6E7385')
  const accent = readToken('--accent', '#7DD3FC')

  trendChart.setOption({
    backgroundColor: 'transparent',
    color: [accent],
    tooltip: {
      trigger: 'axis',
      backgroundColor: bgElev1,
      borderColor: lineStrong,
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: textPrimary, fontFamily: 'JetBrains Mono', fontSize: 11 },
      formatter: (params: { axisValue: string; value: number }[]) => {
        const p = params[0]
        return `${p.axisValue}<br/><b style="color:${textPrimary}">${p.value}</b> 次调用`
      }
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
        name: '调用次数',
        type: 'line',
        smooth: 0.4,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 1.6 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: hexToRgba(accent, 0.25) },
            { offset: 1, color: hexToRgba(accent, 0) }
          ])
        },
        data: trend.map((t) => t.callCount)
      }
    ]
  })
}

function renderCharts() {
  renderSceneRing()
  renderTrendLine()
}

function resize() {
  sceneChart?.resize()
  trendChart?.resize()
}

// ---------------- 数据加载 ----------------
async function loadOverview() {
  overview.value = await getAiStatsOverview()
  await nextTick()
  renderCharts()
}

async function loadSlow() {
  slowLoading.value = true
  try {
    slowList.value = await listSlowAiCalls({ days: 7, limit: 10 })
  } finally {
    slowLoading.value = false
  }
}

async function reloadLogs() {
  logsLoading.value = true
  try {
    const r = await listAiCallLogs({
      scene: filters.scene || undefined,
      modelName: filters.modelName || undefined,
      status: filters.status || undefined,
      page: filters.page,
      size: filters.size
    })
    logsPage.value = { total: r.total, records: r.records }
  } finally {
    logsLoading.value = false
  }
}

function onSizeChange() {
  filters.page = 1
  reloadLogs()
}

async function loadAll() {
  error500.value = null
  try {
    await Promise.all([loadOverview(), loadSlow(), reloadLogs()])
  } catch (e: any) {
    if (e?.response?.status >= 500) {
      error500.value = { retry: loadAll }
    }
  }
}

// 慢调用展开 / 收起
async function toggleExpand(log: AiCallLogItem) {
  if (expandedId.value === log.id) {
    expandedId.value = null
    expandedDetail.value = null
    return
  }
  expandedId.value = log.id
  expandedDetail.value = null
  expandedDetail.value = await getAiCallLog(log.id)
}

async function openDetail(log: AiCallLogItem) {
  detailVisible.value = true
  detail.value = null
  detail.value = await getAiCallLog(log.id)
}

// ---------------- 工具 ----------------
function formatNum(v?: number | null) {
  if (v == null) return '0'
  return v.toLocaleString('en-US')
}

function formatPct(v?: number | null) {
  if (v == null) return '0.0%'
  return `${v.toFixed(1)}%`
}

function formatMoney(v?: number | string | null) {
  if (v == null) return '0.00'
  const n = typeof v === 'string' ? parseFloat(v) : v
  if (Number.isNaN(n)) return '0.00'
  return n.toFixed(2)
}

function formatTime(t?: string) {
  return t ? dayjs(t).format('MM-DD HH:mm:ss') : '-'
}

// ---------------- 环比指示组件 ----------------
const Delta = defineComponent({
  props: {
    today: { type: Number, required: true },
    yesterday: { type: Number, required: true },
    isPct: { type: Boolean, default: false }
  },
  setup(props) {
    return () => {
      const { today, yesterday, isPct } = props
      let sign: '+' | '-' | '=' = '='
      let label = '持平'
      if (yesterday === 0 && today === 0) {
        sign = '='
        label = '持平'
      } else if (yesterday === 0) {
        sign = '+'
        label = '新增'
      } else {
        const delta = ((today - yesterday) / yesterday) * 100
        if (Math.abs(delta) < 0.5) {
          sign = '='
          label = '持平'
        } else {
          sign = delta > 0 ? '+' : '-'
          label = `${Math.abs(delta).toFixed(0)}%`
        }
      }
      // 空数据时不渲染（Req 8.5）
      if (today === 0 && yesterday === 0) {
        return h('span', { class: 'delta empty' }, '—')
      }
      return h('span', { class: ['delta', sign === '+' ? 'up' : sign === '-' ? 'down' : 'flat'] }, [
        h('span', { class: 'arrow' }, sign === '+' ? '↑' : sign === '-' ? '↓' : '•'),
        h('span', { class: 'pct' }, label),
        isPct ? h('span', { class: 'unit' }, '点') : null
      ])
    }
  }
})

// ---------------- 生命周期 ----------------
onMounted(() => {
  loadAll()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  sceneChart?.dispose()
  trendChart?.dispose()
})

watch(() => theme.isDark, () => {
  requestAnimationFrame(() => {
    if (overview.value) renderCharts()
  })
})

// 流水筛选改变时重置到第 1 页
watch(() => [filters.scene, filters.status, filters.modelName], () => {
  filters.page = 1
})
</script>

<style scoped>
.ai-stats-v {
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
  padding: 28px 0;
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

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.dot-anim {
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
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 84px;
  letter-spacing: -0.05em;
  line-height: 0.85;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.hero-words { padding-bottom: 6px; }

.hero-line-1 {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.hero-line-2 {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
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

.v-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

@media (max-width: 1280px) { .hero-right { display: none; } }

.delta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  justify-content: flex-end;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 500;
}
.delta.up   { color: var(--ok); }
.delta.down { color: var(--critical); }
.delta.flat { color: var(--text-muted); }
.delta.empty { color: var(--text-faint); font-size: 14px; }
.delta .arrow { font-size: 14px; }
.delta .unit { font-size: 11px; color: var(--text-muted); }

/* ========== KPI ========== */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

@media (max-width: 1280px) {
  .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

/* ========== Panel block ========== */
.panel-block {
  position: relative;
  display: grid;
  gap: 14px;
  padding: 20px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
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
  flex-wrap: wrap;
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

.chart-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 14px;
}

@media (max-width: 1100px) {
  .chart-row { grid-template-columns: 1fr; }
}

.chart {
  width: 100%;
  height: 260px;
  margin-top: 6px;
}

/* ========== Slow table ========== */
.slow-table,
.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.slow-table th,
.log-table th {
  text-align: left;
  padding: 8px 12px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
  border-bottom: 1px solid var(--line);
}

.slow-table th.num,
.log-table th.num {
  text-align: right;
}

.slow-table td,
.log-table td {
  padding: 10px 12px;
  border-bottom: 1px dashed var(--line);
  color: var(--text-secondary);
  vertical-align: middle;
}

.slow-table th.rank,
.slow-table td.rank {
  width: 36px;
  text-align: center;
  color: var(--text-faint);
}

.slow-table .dur,
.log-table .num {
  text-align: right;
  color: var(--text-primary);
  font-family: var(--font-mono);
}

.slow-table .time,
.log-table .time {
  font-family: var(--font-mono);
  color: var(--text-muted);
}

.slow-table .model,
.log-table .model {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-secondary);
}

.scene-tag {
  padding: 1px 7px;
  border: 1px solid;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.st-pill {
  padding: 1px 8px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.st-pill.ok   { background: var(--ok-soft); color: var(--ok); }
.st-pill.fail { background: var(--danger-soft); color: var(--danger); }

.data-row { transition: background 0.12s ease; cursor: pointer; }
.data-row:hover { background: var(--bg-elev-2); }

.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
}

.link-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.expand-row td {
  background: var(--bg-elev-2);
  padding: 12px 16px;
}

.payload-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 900px) {
  .payload-grid { grid-template-columns: 1fr; }
}

.payload {
  display: grid;
  gap: 6px;
}

.payload-head {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.payload pre,
.payload-section pre {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.5;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.payload-loading {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
}

.empty-row td {
  padding: 24px 12px;
  border-bottom: 0;
}

.empty-mini {
  text-align: center;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}

/* ========== Filters ========== */
.filters {
  display: flex;
  gap: 8px;
}

.f-select { width: 140px; }
.f-input { width: 200px; }

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
}

/* ========== Drawer ========== */
:deep(.ai-call-drawer .el-drawer) {
  background: var(--bg-elev-1);
}

.drawer-content {
  display: grid;
  gap: 16px;
  padding: 22px 24px;
}

.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.drawer-title {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  color: var(--text-primary);
}

.close {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.close:hover {
  border-color: var(--line-strong);
  color: var(--text-primary);
}

.drawer-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.meta-cell {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  font-size: 12px;
}

.meta-cell .lbl {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.meta-cell .val {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-primary);
  font-weight: 500;
}

.error-block {
  display: inline-flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid rgba(248, 113, 113, 0.35);
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12.5px;
  line-height: 1.6;
}

.payload-section {
  display: grid;
  gap: 6px;
}

@media (max-width: 760px) {
  .ai-stats-v { padding: 0 14px 24px; }
  .hero-num { font-size: 64px; }
  .filters { flex-wrap: wrap; }
  .f-input, .f-select { flex: 1; min-width: 120px; }
}
</style>
