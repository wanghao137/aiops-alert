<template>
  <div class="dashboard-view" v-loading="loading">
    <PageHeader
      eyebrow="OVERVIEW"
      title="智能告警总览"
      subtitle="实时反映监控告警全貌：当前态势、告警分布、命中规则、最近事件。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="BellIcon" @click="$router.push('/events')">查看事件中心</el-button>
      </template>
    </PageHeader>

    <!-- 顶部 4 大数字卡 -->
    <section class="hero-row">
      <div class="hero-card primary">
        <div class="hero-icon"><BellIcon :size="22" /></div>
        <div class="hero-meta">
          <span>告警事件总数</span>
          <strong class="num tabular-nums">{{ data?.eventTotal || 0 }}</strong>
          <small>当前库内累计</small>
        </div>
      </div>
      <div class="hero-card warn">
        <div class="hero-icon"><ClockIcon :size="22" /></div>
        <div class="hero-meta">
          <span>待处理</span>
          <strong class="num tabular-nums">{{ data?.pendingEventTotal || 0 }}</strong>
          <small>需要优先确认</small>
        </div>
      </div>
      <div class="hero-card danger">
        <div class="hero-icon"><FlameIcon :size="22" /></div>
        <div class="hero-meta">
          <span>紧急 / 严重</span>
          <strong class="num tabular-nums">{{ (data?.criticalEventTotal || 0) + (data?.seriousEventTotal || 0) }}</strong>
          <small>紧急 {{ data?.criticalEventTotal || 0 }} · 严重 {{ data?.seriousEventTotal || 0 }}</small>
        </div>
      </div>
      <div class="hero-card incident">
        <div class="hero-icon"><LayersIcon :size="22" /></div>
        <div class="hero-meta">
          <span>活跃 Incident</span>
          <strong class="num tabular-nums">{{ data?.openIncidentTotal || 0 }}</strong>
          <small>同对象多告警归并</small>
        </div>
      </div>
    </section>

    <!-- 资源条 -->
    <section class="resource-row">
      <div>
        <span>监控对象</span>
        <strong>{{ data?.enabledObjectTotal || 0 }} / {{ data?.objectTotal || 0 }}</strong>
      </div>
      <div>
        <span>告警规则</span>
        <strong>{{ data?.enabledRuleTotal || 0 }} / {{ data?.ruleTotal || 0 }}</strong>
      </div>
      <div>
        <span>通知渠道</span>
        <strong>{{ data?.enabledChannelTotal || 0 }} / {{ data?.channelTotal || 0 }}</strong>
      </div>
      <div>
        <span>今日通知失败</span>
        <strong :class="{ 'text-warn': (data?.notifyFailedToday || 0) > 0 }">{{ data?.notifyFailedToday || 0 }}</strong>
      </div>
    </section>

    <!-- 趋势 + 分布 -->
    <section class="grid">
      <div class="panel wide">
        <div class="panel-title">
          <h3>近 7 天告警趋势</h3>
          <p>按首次触发时间统计：总量 / 待处理 / 已恢复 / 紧急</p>
        </div>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">
          <h3>事件状态分布</h3>
          <p>处理闭环进度</p>
        </div>
        <div ref="statusRef" class="chart small"></div>
      </div>
      <div class="panel">
        <div class="panel-title">
          <h3>告警级别分布</h3>
          <p>风险占比</p>
        </div>
        <div ref="levelRef" class="chart small"></div>
      </div>

      <div class="panel wide">
        <div class="panel-title">
          <h3>规则命中 Top</h3>
          <p>近 7 天命中事件数最多的规则，提示是否需要调整阈值</p>
        </div>
        <div class="hit-list">
          <div v-for="(it, i) in data?.ruleHitTop || []" :key="`${it.ruleId}-${i}`" class="hit-row">
            <span class="rank">{{ i + 1 }}</span>
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

      <div class="panel">
        <div class="panel-title">
          <h3>对象类型分布</h3>
          <p>定位主要告警来源</p>
        </div>
        <div class="dist-list">
          <div v-for="d in data?.objectTypeDistribution || []" :key="d.code" class="dist-row">
            <component :is="getObjectTypeMeta(d.code).icon" :size="14"
              :style="{ color: getObjectTypeMeta(d.code).color }" />
            <span>{{ d.name }}</span>
            <strong class="tabular-nums">{{ d.value }}</strong>
          </div>
        </div>
      </div>

      <div class="panel wide">
        <div class="panel-title">
          <h3>最近事件</h3>
          <p>按最近触发时间倒序，点击进入事件中心</p>
          <el-button text type="primary" @click="$router.push('/events')">更多</el-button>
        </div>
        <div class="recent-list">
          <div v-for="ev in data?.recentEvents || []" :key="ev.id" class="recent-row"
            @click="$router.push('/events')">
            <span class="lv-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
            <div class="recent-meta">
              <div class="recent-row1">
                <span class="recent-title">{{ ev.eventTitle }}</span>
                <span class="lv-pill" :style="{
                  background: getAlertLevelMeta(ev.alertLevel).bg,
                  color: getAlertLevelMeta(ev.alertLevel).color
                }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
              </div>
              <div class="recent-row2">
                <span>{{ ev.objectName }}</span>
                <span class="dot-sep" />
                <span>{{ ev.metricName }}</span>
                <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
                <span class="dot-sep" />
                <span class="muted">{{ formatTime(ev.lastTriggeredAt) }}</span>
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
import {
  RefreshCw as RefreshIcon,
  Bell as BellIcon,
  Clock as ClockIcon,
  Flame as FlameIcon,
  Layers as LayersIcon
} from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import { getObjectTypeMeta } from '@/utils/objectType'
import { getAlertLevelMeta } from '@/utils/alertLevel'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import { useRealtimeStore } from '@/stores/realtime'

const data = ref<DashboardData>()
const loading = ref(false)
const trendRef = ref<HTMLDivElement>()
const statusRef = ref<HTMLDivElement>()
const levelRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | undefined
let statusChart: echarts.ECharts | undefined
let levelChart: echarts.ECharts | undefined

const realtime = useRealtimeStore()

const maxHit = computed(() => {
  const arr = data.value?.ruleHitTop || []
  return arr.reduce((m, x) => Math.max(m, x.hitCount), 0) || 1
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
  renderPie(statusRef.value, 'status', data.value?.statusDistribution || [])
  renderPie(levelRef.value, 'level', data.value?.levelDistribution || [])
}

function renderTrend() {
  if (!trendRef.value || !data.value) return
  trendChart = trendChart || echarts.init(trendRef.value, 'dark')
  const trend = data.value.sevenDayTrend || []
  trendChart.setOption({
    backgroundColor: 'transparent',
    color: ['#3B82F6', '#F59E0B', '#10B981', '#EF4444'],
    tooltip: { trigger: 'axis', backgroundColor: '#1F2937', borderColor: '#374151', textStyle: { color: '#F8FAFC' } },
    legend: { top: 0, right: 0, textStyle: { color: '#94A3B8' } },
    grid: { left: 36, right: 18, top: 40, bottom: 28 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date.slice(5)),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#374151' } },
      axisLabel: { color: '#94A3B8' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)' } },
      axisLabel: { color: '#94A3B8' }
    },
    series: [
      { name: '总量', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        areaStyle: { color: 'rgba(59,130,246,0.18)' }, data: trend.map((t) => t.total) },
      { name: '待处理', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: trend.map((t) => t.pending) },
      { name: '已恢复', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: trend.map((t) => t.recovered) },
      { name: '紧急', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: trend.map((t) => t.critical) }
    ]
  })
}

function renderPie(el: HTMLDivElement | undefined, kind: 'status' | 'level',
                   list: { code: string; name: string; value: number }[]) {
  if (!el) return
  const chart = kind === 'status'
    ? (statusChart = statusChart || echarts.init(el, 'dark'))
    : (levelChart = levelChart || echarts.init(el, 'dark'))
  chart.setOption({
    backgroundColor: 'transparent',
    color: kind === 'level'
      ? ['#0EA5E9', '#3B82F6', '#F59E0B', '#EF4444']
      : ['#F59E0B', '#3B82F6', '#10B981', '#94A3B8'],
    tooltip: { trigger: 'item', backgroundColor: '#1F2937', borderColor: '#374151', textStyle: { color: '#F8FAFC' } },
    legend: { bottom: 0, left: 'center', textStyle: { color: '#94A3B8' } },
    series: [{
      type: 'pie',
      radius: ['52%', '72%'],
      center: ['50%', '42%'],
      label: { color: '#F8FAFC', formatter: '{b}: {c}' },
      itemStyle: { borderColor: '#0F172A', borderWidth: 2 },
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
  return t ? dayjs(t).format('MM-DD HH:mm:ss') : '-'
}

// 实时事件来时刷新
watch(() => realtime.lastEventId, () => loadAll())

onMounted(() => {
  loadAll()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  statusChart?.dispose()
  levelChart?.dispose()
})
</script>

<style scoped>
.dashboard-view { display: grid; gap: 16px; }

.hero-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.hero-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--bg-panel);
  transition: transform 0.15s ease;
  position: relative;
  overflow: hidden;
}

.hero-card:hover { transform: translateY(-2px); }

.hero-card.primary {
  background:
    radial-gradient(circle at 100% 0%, rgba(59,130,246,0.18), transparent 60%),
    var(--bg-panel);
  border-color: rgba(59,130,246,0.3);
}

.hero-card.warn {
  background:
    radial-gradient(circle at 100% 0%, rgba(245,158,11,0.18), transparent 60%),
    var(--bg-panel);
  border-color: rgba(245,158,11,0.3);
}

.hero-card.danger {
  background:
    radial-gradient(circle at 100% 0%, rgba(239,68,68,0.18), transparent 60%),
    var(--bg-panel);
  border-color: rgba(239,68,68,0.3);
}

.hero-card.incident {
  background:
    radial-gradient(circle at 100% 0%, rgba(139,92,246,0.18), transparent 60%),
    var(--bg-panel);
  border-color: rgba(139,92,246,0.3);
}

.hero-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--bg-subtle);
}

.hero-card.primary .hero-icon { color: #93C5FD; }
.hero-card.warn .hero-icon { color: #FCD34D; }
.hero-card.danger .hero-icon { color: #FCA5A5; }
.hero-card.incident .hero-icon { color: #C4B5FD; }

.hero-meta { display: grid; gap: 2px; min-width: 0; }
.hero-meta span { color: var(--text-muted); font-size: 12px; }
.hero-meta .num { color: var(--text-primary); font-size: 32px; font-weight: 600; line-height: 1.1; }
.hero-meta small { color: var(--text-muted); font-size: 11px; }

@media (max-width: 1280px) { .hero-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

.resource-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
}

.resource-row > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 18px;
}

.resource-row > div + div {
  border-left: 1px solid var(--line);
}

.resource-row span { color: var(--text-muted); font-size: 12px; }
.resource-row strong { color: var(--text-primary); font-size: 18px; font-variant-numeric: tabular-nums; }
.resource-row strong.text-warn { color: #FCA5A5; }

@media (max-width: 1280px) {
  .resource-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .resource-row > div:nth-child(3) { border-left: 0; border-top: 1px solid var(--line); }
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.panel {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
  min-width: 0;
}

.panel.wide { grid-column: span 2; }

@media (max-width: 1100px) {
  .grid { grid-template-columns: 1fr; }
  .panel.wide { grid-column: 1 / -1; }
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-title h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.panel-title p {
  margin: 2px 0 0;
  color: var(--text-muted);
  font-size: 11px;
}

.chart {
  width: 100%;
  height: 280px;
}

.chart.small { height: 240px; }

.hit-list { display: grid; gap: 6px; }

.hit-row {
  display: grid;
  grid-template-columns: 24px 1fr 100px auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
}

.rank {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: rgba(59,130,246,0.18);
  color: #93C5FD;
  font-size: 11px;
  font-weight: 700;
}

.hit-meta { display: grid; gap: 1px; min-width: 0; }
.hit-meta strong {
  color: var(--text-primary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hit-meta small { color: var(--text-muted); font-size: 11px; }

.hit-bar {
  height: 6px;
  background: var(--bg-panel);
  border-radius: 3px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #3B82F6, #8B5CF6);
  border-radius: 3px;
}

.dist-list { display: grid; gap: 8px; }

.dist-row {
  display: grid;
  grid-template-columns: 16px 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
}

.dist-row span {
  color: var(--text-secondary);
  font-size: 12px;
}

.dist-row strong { color: var(--text-primary); font-size: 14px; }

.recent-list { display: grid; gap: 6px; }

.recent-row {
  display: grid;
  grid-template-columns: 4px 1fr;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
  cursor: pointer;
  transition: all 0.15s ease;
}

.recent-row:hover {
  border-color: var(--line-subtle);
}

.lv-bar {
  border-radius: 4px;
}

.recent-meta { display: grid; gap: 3px; min-width: 0; }

.recent-row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.recent-title {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lv-pill {
  display: inline-flex;
  align-items: center;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.recent-row2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 11px;
}

.recent-row2 code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-panel);
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.recent-row2 .muted { color: var(--text-muted); }

.dot-sep {
  width: 3px; height: 3px;
  background: var(--text-muted);
  border-radius: 50%;
  opacity: 0.5;
}

.empty-mini {
  padding: 30px 12px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
