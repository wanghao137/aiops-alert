<template>
  <div class="incidents-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">故障组 / INCIDENTS</span>
          <span class="dot-anim" />
          <span class="hero-time">同对象 30 分钟自动归并</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num" :class="{ urgent: counts.open > 0 }">{{ counts.open }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ counts.open > 0 ? '进行中故障' : '无活跃故障' }}</div>
            <div class="hero-line-2">
              累计 {{ incidents.length }} · 已关闭 {{ counts.closed }} · 共关联 {{ counts.events }} 条事件
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <button class="hero-action" @click="loadAll">
          <RefreshIcon :size="13" :stroke-width="1.6" /> 刷新
        </button>
      </div>
    </section>

    <!-- ========== 过滤条 ========== -->
    <section class="toolbar">
      <div class="seg">
        <button
          class="seg-item"
          :class="{ active: filter.status === '' }"
          @click="filter.status = ''; loadAll()"
        >全部</button>
        <button
          class="seg-item"
          :class="{ active: filter.status === 'OPEN' }"
          @click="filter.status = 'OPEN'; loadAll()"
        >进行中</button>
        <button
          class="seg-item"
          :class="{ active: filter.status === 'CLOSED' }"
          @click="filter.status = 'CLOSED'; loadAll()"
        >已关闭</button>
      </div>

      <el-select v-model="filter.objectType" placeholder="对象类型" clearable
        class="type-select" @change="loadAll">
        <el-option v-for="t in OBJECT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
    </section>

    <!-- ========== Incident 列表 ========== -->
    <section v-loading="loading && incidents.length > 0" class="incident-list">
      <SkeletonList v-if="loading && incidents.length === 0" :rows="4" />
      <article v-for="inc in incidents" :key="inc.id" class="incident-card">
        <header class="ic-head">
          <span class="ic-strip" :style="{ background: getAlertLevelMeta(inc.topLevel).color }" />
          <div class="ic-meta">
            <div class="ic-row1">
              <component :is="getObjectTypeMeta(inc.objectType).icon" :size="14" :stroke-width="1.6"
                :style="{ color: getObjectTypeMeta(inc.objectType).color }" />
              <span class="obj-name">{{ inc.objectName }}</span>
              <span class="lv-tag" :style="{
                color: getAlertLevelMeta(inc.topLevel).color,
                borderColor: getAlertLevelMeta(inc.topLevel).color
              }">{{ getAlertLevelMeta(inc.topLevel).label }}</span>
              <span :class="['st-tag', inc.status === 'OPEN' ? 'open' : 'closed']">
                {{ inc.status === 'OPEN' ? '进行中' : '已关闭' }}
              </span>
            </div>
            <div class="ic-row2">
              <span class="no">{{ inc.incidentNo }}</span>
              <span class="sep">·</span>
              <span class="evcount tabular-nums">{{ inc.eventCount }} 条告警</span>
              <span class="sep">·</span>
              <span class="tabular-nums">{{ formatTime(inc.firstEventAt) }} → {{ formatTime(inc.lastEventAt) }}</span>
              <span v-if="duration(inc)" class="sep">·</span>
              <span v-if="duration(inc)" class="dur tabular-nums">持续 {{ duration(inc) }}</span>
            </div>
          </div>

          <div class="ic-stat">
            <div class="ic-stat-num tabular-nums">{{ inc.eventCount }}</div>
            <div class="ic-stat-lbl">事件 EVENTS</div>
          </div>
        </header>

        <!-- Timeline -->
        <div class="timeline">
          <div v-for="(ev, idx) in inc.events || []" :key="ev.id" class="tl-row">
            <div class="tl-time tabular-nums">{{ formatTime(ev.lastTriggeredAt) }}</div>
            <div class="tl-dot-col">
              <div class="tl-dot" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }">
                <span class="tl-num">{{ String((inc.events?.length || 0) - idx).padStart(2, '0') }}</span>
              </div>
              <div v-if="idx !== (inc.events?.length || 0) - 1" class="tl-line" />
            </div>
            <div class="tl-card">
              <div class="tl-row1">
                <span class="tl-title">{{ ev.eventTitle }}</span>
                <span class="lv-tag mini" :style="{
                  color: getAlertLevelMeta(ev.alertLevel).color,
                  borderColor: getAlertLevelMeta(ev.alertLevel).color
                }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
                <span :class="['st-pill', evStatusClass(ev.eventStatus)]">{{ evStatusName(ev.eventStatus) }}</span>
              </div>
              <div class="tl-row2">
                <span class="metric">{{ ev.metricName }}</span>
                <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
              </div>
              <div v-if="ev.eventReason" class="tl-row3">{{ ev.eventReason }}</div>
            </div>
          </div>
          <div v-if="!inc.events?.length" class="tl-empty">暂无关联事件</div>
        </div>
      </article>

      <div v-if="!loading && !incidents.length" class="empty">
        <Flame :size="28" :stroke-width="1.4" />
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
  Flame
} from 'lucide-vue-next'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import { getAlertLevelMeta } from '@/utils/alertLevel'
import SkeletonList from '@/components/common/SkeletonList.vue'
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
.incidents-v {
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

.hero-num.urgent { color: var(--critical); }

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

.hero-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.hero-action:hover {
  border-color: var(--accent-line);
  color: var(--accent);
}

/* ========== Toolbar ========== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.seg {
  display: inline-flex;
  padding: 2px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
}

.seg-item {
  padding: 6px 14px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.seg-item:hover { color: var(--text-primary); }
.seg-item.active {
  background: var(--accent-soft);
  color: var(--accent);
}

.type-select { width: 160px; }

/* ========== Incident card ========== */
.incident-list { display: grid; gap: 14px; }

.incident-card {
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  display: grid;
  gap: 18px;
}

.ic-head {
  display: grid;
  grid-template-columns: 3px 1fr auto;
  gap: 16px;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--line);
}

.ic-strip {
  align-self: stretch;
  border-radius: 3px;
}

.ic-meta { display: grid; gap: 6px; min-width: 0; }

.ic-row1 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.obj-name {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.lv-tag {
  padding: 1px 8px;
  border: 1px solid;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.lv-tag.mini {
  padding: 0 7px;
  font-size: 9.5px;
}

.st-tag {
  padding: 2px 9px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.st-tag.open {
  background: var(--critical-soft);
  color: var(--critical);
}

.st-tag.closed {
  background: var(--bg-elev-3);
  color: var(--text-muted);
}

.ic-row2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.ic-row2 .no { color: var(--text-secondary); }
.ic-row2 .sep { color: var(--text-faint); }
.ic-row2 .evcount { color: var(--text-secondary); }
.ic-row2 .dur { color: var(--accent); }

/* 右侧大数字 */
.ic-stat {
  text-align: right;
  padding-left: 14px;
  border-left: 1px solid var(--line);
}

.ic-stat-num {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 500;
  letter-spacing: -0.04em;
  color: var(--text-primary);
  line-height: 1;
}

.ic-stat-lbl {
  margin-top: 4px;
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

/* Timeline */
.timeline {
  display: grid;
  gap: 0;
  padding-left: 4px;
}

.tl-row {
  display: grid;
  grid-template-columns: 80px 24px 1fr;
  gap: 14px;
  align-items: stretch;
}

.tl-time {
  padding-top: 10px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.tl-dot-col {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tl-dot {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  margin-top: 8px;
  z-index: 1;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px var(--bg-elev-1);
}

.tl-num {
  color: white;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.tl-line {
  flex: 1;
  width: 1px;
  background: var(--line);
}

.tl-card {
  margin: 6px 0 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  display: grid;
  gap: 6px;
}

.tl-row1 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tl-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.st-pill {
  padding: 1px 7px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.st-pill.pending   { color: var(--warn);  background: var(--warn-soft); }
.st-pill.confirmed { color: var(--accent); background: var(--accent-soft); }
.st-pill.recovered { color: var(--ok);    background: var(--ok-soft); }
.st-pill.closed    { color: var(--text-muted); background: var(--bg-elev-3); }

.tl-row2 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-secondary);
}

.tl-row2 code {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-elev-3);
  color: var(--text-primary);
  font-size: 10.5px;
}

.tl-row3 {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
}

.tl-empty {
  padding: 24px;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.1em;
}

/* Empty */
.empty {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 60px 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-md);
  color: var(--text-muted);
}

.empty-title {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.empty-hint { font-size: 12px; }

@media (max-width: 900px) {
  .incidents-v {
    grid-template-columns: minmax(0, 1fr);
  }

  .incident-card {
    padding: 18px 14px;
    overflow: hidden;
  }

  .ic-head {
    grid-template-columns: 3px minmax(0, 1fr);
    gap: 12px;
  }

  .ic-stat {
    display: none;
  }

  .obj-name,
  .tl-title {
    white-space: normal;
  }

  .timeline,
  .tl-row,
  .tl-card {
    min-width: 0;
  }

  .tl-row {
    grid-template-columns: 54px 20px minmax(0, 1fr);
    gap: 8px;
  }

  .tl-time {
    font-size: 10px;
    word-break: break-word;
  }

  .tl-card {
    padding: 10px 12px;
  }

  .toolbar {
    align-items: stretch;
  }

  .type-select {
    width: 100%;
  }
}
</style>
