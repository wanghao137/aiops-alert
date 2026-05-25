<template>
  <div class="incidents-view">
    <PageHeader
      eyebrow="INCIDENTS"
      title="智能告警归并"
      subtitle="同一对象在 30 分钟窗口内的多条告警自动合并为一个 Incident，便于运维聚焦故障，避免刷屏。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <section class="stat-row">
      <StatCard label="进行中" :value="counts.open" :icon="FlameIcon" accent="#EF4444"
        hint="OPEN 状态" />
      <StatCard label="已关闭" :value="counts.closed" :icon="CheckIcon" accent="#10B981" hint="CLOSED" />
      <StatCard label="累计事件" :value="counts.events" :icon="LayersIcon" accent="#3B82F6"
        hint="所有 Incident 包含的事件" />
    </section>

    <section class="filter-bar">
      <el-radio-group v-model="filter.status" @change="loadAll">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="OPEN">进行中</el-radio-button>
        <el-radio-button label="CLOSED">已关闭</el-radio-button>
      </el-radio-group>
      <el-select v-model="filter.objectType" placeholder="对象类型" clearable
        class="type-select" @change="loadAll">
        <el-option v-for="t in OBJECT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
    </section>

    <section v-loading="loading" class="incident-list">
      <article v-for="inc in incidents" :key="inc.id" class="incident-card">
        <header class="head">
          <div class="lv-bar" :style="{ background: getAlertLevelMeta(inc.topLevel).color }" />
          <div class="head-meta">
            <div class="head-title">
              <component :is="getObjectTypeMeta(inc.objectType).icon" :size="14"
                :style="{ color: getObjectTypeMeta(inc.objectType).color }" />
              <span class="obj-name">{{ inc.objectName }}</span>
              <span class="lv-pill" :style="{
                background: getAlertLevelMeta(inc.topLevel).bg,
                color: getAlertLevelMeta(inc.topLevel).color
              }">
                <component :is="getAlertLevelMeta(inc.topLevel).icon" :size="11" />
                {{ getAlertLevelMeta(inc.topLevel).label }}
              </span>
              <span :class="['st-pill', inc.status === 'OPEN' ? 'open' : 'closed']">
                {{ inc.status === 'OPEN' ? '进行中' : '已关闭' }}
              </span>
            </div>
            <div class="head-sub">
              <span>{{ inc.incidentNo }}</span>
              <span class="dot-sep" />
              <span><Flame :size="11" /> 共 {{ inc.eventCount }} 条告警</span>
              <span class="dot-sep" />
              <span><Clock :size="11" /> {{ formatTime(inc.firstEventAt) }} → {{ formatTime(inc.lastEventAt) }}</span>
              <span class="dot-sep" v-if="duration(inc)" />
              <span v-if="duration(inc)">持续 {{ duration(inc) }}</span>
            </div>
          </div>
        </header>

        <!-- 时间线 -->
        <div class="timeline">
          <div v-for="(ev, idx) in inc.events || []" :key="ev.id" class="tl-row">
            <div class="tl-dot" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }">
              <span class="tl-num">{{ (inc.events?.length || 0) - idx }}</span>
            </div>
            <div class="tl-line" v-if="idx !== (inc.events?.length || 0) - 1" />
            <div class="tl-card">
              <div class="tl-row1">
                <span class="tl-title">{{ ev.eventTitle }}</span>
                <span class="lv-pill mini" :style="{
                  background: getAlertLevelMeta(ev.alertLevel).bg,
                  color: getAlertLevelMeta(ev.alertLevel).color
                }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
              </div>
              <div class="tl-row2">
                <span>{{ ev.metricName }}</span>
                <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
                <span class="dot-sep" />
                <span :class="['st-pill', evStatusClass(ev.eventStatus)]">{{ evStatusName(ev.eventStatus) }}</span>
                <span class="dot-sep" />
                <span class="muted">{{ formatTime(ev.lastTriggeredAt) }}</span>
              </div>
              <div v-if="ev.eventReason" class="tl-row3">{{ ev.eventReason }}</div>
            </div>
          </div>
          <div v-if="!inc.events?.length" class="muted center">暂无关联事件</div>
        </div>
      </article>

      <div v-if="!loading && !incidents.length" class="empty">
        <Flame :size="36" />
        <div class="empty-title">暂无 Incident</div>
        <div class="empty-hint">同一对象短时间内多次告警会自动归并到此处。</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import {
  RefreshCw as RefreshIcon,
  Flame as FlameIcon,
  CheckCircle2 as CheckIcon,
  Layers as LayersIcon,
  Flame, Clock
} from 'lucide-vue-next'
import StatCard from '@/components/common/StatCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import { getAlertLevelMeta } from '@/utils/alertLevel'
import { listIncidents, type AlertIncidentItem } from '@/api/alertIncident'

const incidents = ref<AlertIncidentItem[]>([])
const loading = ref(false)
const filter = reactive({ status: '', objectType: '' })

const counts = computed(() => {
  const open = incidents.value.filter((i) => i.status === 'OPEN').length
  const closed = incidents.value.filter((i) => i.status === 'CLOSED').length
  const events = incidents.value.reduce((acc, i) => acc + (i.eventCount || 0), 0)
  return { open, closed, events }
})

async function loadAll() {
  loading.value = true
  try {
    incidents.value = await listIncidents({
      status: filter.status || undefined,
      objectType: filter.objectType || undefined
    })
  } finally {
    loading.value = false
  }
}

function evStatusName(s?: string) {
  return ({ PENDING: '待处理', CONFIRMED: '已确认', RECOVERED: '已恢复', CLOSED: '已关闭' } as Record<string, string>)[s || ''] || s || ''
}

function evStatusClass(s?: string) {
  if (s === 'PENDING') return 'pending'
  if (s === 'CONFIRMED') return 'confirmed'
  if (s === 'RECOVERED') return 'recovered'
  if (s === 'CLOSED') return 'closed'
  return ''
}

function formatTime(t?: string) {
  return t ? dayjs(t).format('MM-DD HH:mm:ss') : '-'
}

function duration(inc: AlertIncidentItem) {
  if (!inc.firstEventAt || !inc.lastEventAt) return ''
  const diff = dayjs(inc.lastEventAt).diff(inc.firstEventAt, 'second')
  if (diff < 60) return `${diff}s`
  if (diff < 3600) return `${Math.floor(diff / 60)}m`
  return `${(diff / 3600).toFixed(1)}h`
}

onMounted(loadAll)
</script>

<style scoped>
.incidents-view { display: grid; gap: 16px; }

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
}

.type-select { width: 160px; }

.incident-list { display: grid; gap: 12px; }

.incident-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
  display: grid;
  gap: 14px;
}

.head {
  display: grid;
  grid-template-columns: 4px 1fr;
  gap: 12px;
}

.lv-bar {
  border-radius: 4px;
}

.head-meta { display: grid; gap: 6px; }

.head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.obj-name {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.lv-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.lv-pill.mini {
  padding: 1px 7px;
  font-size: 10px;
}

.st-pill {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.st-pill.open { background: rgba(239,68,68,0.15); color: #FCA5A5; }
.st-pill.closed { background: rgba(148,163,184,0.15); color: #94A3B8; }
.st-pill.pending   { background: rgba(245, 158, 11, 0.15); color: #FCD34D; }
.st-pill.confirmed { background: rgba(59, 130, 246, 0.15); color: #93C5FD; }
.st-pill.recovered { background: rgba(16, 185, 129, 0.15); color: #6EE7B7; }

.head-sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.head-sub > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dot-sep {
  width: 3px; height: 3px;
  background: var(--text-muted);
  border-radius: 50%;
  opacity: 0.5;
  display: inline-block;
}

.timeline {
  display: grid;
  gap: 0;
  padding: 8px 0;
  border-top: 1px dashed var(--line);
}

.tl-row {
  position: relative;
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
  padding: 12px 0 12px 6px;
}

.tl-dot {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  z-index: 1;
}

.tl-num {
  color: white;
  font-size: 10px;
  font-weight: 700;
}

.tl-line {
  position: absolute;
  left: 16px;
  top: 32px;
  bottom: -6px;
  width: 2px;
  background: var(--line);
  z-index: 0;
}

.tl-card {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
  display: grid;
  gap: 4px;
}

.tl-row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tl-title {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tl-row2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.tl-row2 code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-panel);
  font-family: 'JetBrains Mono', monospace;
}

.tl-row3 {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.muted { color: var(--text-muted); font-size: 12px; }
.center { text-align: center; padding: 20px 0; }

.empty {
  display: grid;
  place-items: center;
  gap: 6px;
  padding: 60px 20px;
  border: 1px dashed var(--line-subtle);
  border-radius: 12px;
  color: var(--text-muted);
}

.empty-title { color: var(--text-primary); font-size: 14px; font-weight: 600; }
.empty-hint  { font-size: 12px; }
</style>
