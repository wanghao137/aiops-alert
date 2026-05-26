<template>
  <section class="brief-card" :class="status">
    <header class="head">
      <div class="left">
        <span class="brand">
          <Sparkles :size="11" :stroke-width="1.8" />
          AI BRIEF
        </span>
        <span class="cov">{{ brief?.coverageDate || '—' }}</span>
        <span v-if="brief?.status === 'FALLBACK'" class="warn-pill">无 LLM · 模板</span>
        <span v-else-if="brief?.status === 'FAILED'" class="fail-pill">AI 失败 · 模板</span>
        <span v-else-if="brief?.status === 'SUCCESS'" class="ok-pill">
          <span class="dot" /> 已生成
        </span>
      </div>
      <div class="right">
        <span v-if="brief?.snapshot" class="dod" :class="dodClass">
          <component :is="dodIcon" :size="11" :stroke-width="1.8" />
          {{ dodLabel }}
        </span>
        <button class="refresh" :disabled="refreshing" @click="onRefresh">
          <RefreshCw :size="12" :stroke-width="1.8" :class="{ spinning: refreshing }" />
          {{ refreshing ? '生成中…' : '刷新简报' }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="narrative-loading">
      <span class="prompt-mark">▸</span>
      <span class="thinking-text">{{ thinkingText }}</span>
      <span class="caret" />
    </div>
    <div v-else class="narrative">
      <span class="prompt-mark">▸</span>
      <span>{{ brief?.narrative || '尚未生成今日简报' }}</span>
    </div>

    <div v-if="brief?.snapshot" class="snap-row">
      <div class="snap-cell">
        <span class="lbl">EVENTS</span>
        <strong class="val tabular-nums">{{ brief.snapshot.totalEvents }}</strong>
      </div>
      <div class="snap-cell critical">
        <span class="lbl">CRITICAL</span>
        <strong class="val tabular-nums">{{ brief.snapshot.criticalEvents }}</strong>
      </div>
      <div class="snap-cell">
        <span class="lbl">PENDING</span>
        <strong class="val tabular-nums">{{ brief.snapshot.pendingEvents }}</strong>
      </div>
      <div class="snap-cell ok">
        <span class="lbl">RECOVERED</span>
        <strong class="val tabular-nums">{{ brief.snapshot.recoveredEvents }}</strong>
      </div>
      <div class="snap-cell">
        <span class="lbl">INCIDENTS</span>
        <strong class="val tabular-nums">{{ brief.snapshot.openIncidents }}</strong>
      </div>
      <div class="snap-cell" :class="{ warn: brief.snapshot.notifyFailed > 0 }">
        <span class="lbl">NOTIFY FAIL</span>
        <strong class="val tabular-nums">{{ brief.snapshot.notifyFailed }}</strong>
      </div>
    </div>

    <div v-if="brief?.highlights?.length" class="highlights">
      <div class="hl-eyebrow">HIGHLIGHTS · TOP {{ brief.highlights.length }}</div>
      <div class="hl-list">
        <article v-for="h in brief.highlights" :key="h.id" class="hl-row" @click="goEvent(h.id)">
          <span class="hl-bar" :style="{ background: levelColor(h.alertLevel) }" />
          <div class="hl-meta">
            <div class="hl-row1">
              <span class="hl-title">{{ h.eventTitle }}</span>
              <span class="lv-tag" :style="{ color: levelColor(h.alertLevel), borderColor: levelColor(h.alertLevel) }">
                {{ levelLabel(h.alertLevel) }}
              </span>
            </div>
            <div class="hl-row2">
              <span>{{ h.objectName }}</span>
              <span class="sep">·</span>
              <span class="tabular-nums">{{ h.triggeredAt || '-' }}</span>
              <span class="sep">·</span>
              <span :class="['st', stClass(h.eventStatus)]">{{ stLabel(h.eventStatus) }}</span>
            </div>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Sparkles, RefreshCw, ArrowUp, ArrowDown, Minus } from 'lucide-vue-next'
import { getDailyBrief, refreshDailyBrief, type DailyBrief } from '@/api/dailyBrief'
import { getAlertLevelMeta } from '@/utils/alertLevel'

const brief = ref<DailyBrief | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const router = useRouter()

const status = computed(() => {
  if (loading.value) return 'loading'
  if (!brief.value) return 'empty'
  return brief.value.status?.toLowerCase() || 'success'
})

// 终端打字机思考效果（refresh 时用）
const THINKING_MESSAGES = [
  '汇总昨日告警数据…',
  '识别异常对象与场景…',
  '生成态势叙述…',
  '组装重点事件清单…'
]
const thinkingText = ref(THINKING_MESSAGES[0])
let thinkingIdx = 0
let thinkingTimer: ReturnType<typeof setInterval> | undefined

function startThinking() {
  if (thinkingTimer) return
  thinkingIdx = 0
  thinkingText.value = THINKING_MESSAGES[0]
  thinkingTimer = setInterval(() => {
    thinkingIdx = (thinkingIdx + 1) % THINKING_MESSAGES.length
    thinkingText.value = THINKING_MESSAGES[thinkingIdx]
  }, 1500)
}

function stopThinking() {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = undefined
  }
}

watch(loading, (v) => {
  if (v) startThinking()
  else stopThinking()
})

async function load() {
  loading.value = true
  try {
    brief.value = await getDailyBrief()
  } catch {
    /* 容错：dashboard 不应因简报失败而整体崩溃 */
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  loading.value = true
  try {
    brief.value = await refreshDailyBrief()
  } catch {
    /* 静默 */
  } finally {
    refreshing.value = false
    loading.value = false
  }
}

const dodClass = computed(() => {
  if (!brief.value?.snapshot) return 'flat'
  const v = brief.value.snapshot.dayOverDay
  if (v > 0.5) return 'up'
  if (v < -0.5) return 'down'
  return 'flat'
})
const dodIcon = computed(() => {
  if (dodClass.value === 'up') return ArrowUp
  if (dodClass.value === 'down') return ArrowDown
  return Minus
})
const dodLabel = computed(() => {
  if (!brief.value?.snapshot) return '持平'
  const v = brief.value.snapshot.dayOverDay
  if (Math.abs(v) < 0.5) return '与前日持平'
  return `vs 前日 ${v > 0 ? '+' : ''}${v.toFixed(0)}%`
})

function levelColor(lv: string) {
  return getAlertLevelMeta(lv).color
}
function levelLabel(lv: string) {
  return getAlertLevelMeta(lv).label
}
function stLabel(s: string) {
  return ({ PENDING: '待处理', CONFIRMED: '已确认', RECOVERED: '已恢复', CLOSED: '已关闭' } as Record<string, string>)[s] || s
}
function stClass(s: string) {
  return s?.toLowerCase() || ''
}

function goEvent(id: number) {
  router.push({ path: '/events', query: { focus: id } })
}

onMounted(load)
onBeforeUnmount(stopThinking)
</script>

<style scoped>
.brief-card {
  position: relative;
  display: grid;
  gap: 14px;
  padding: 18px 22px;
  border: 1px solid var(--accent-line);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, var(--accent-soft) 0%, transparent 60%),
    var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
}

.brief-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  background-size: 200% 100%;
  animation: shimmer 6s linear infinite;
  pointer-events: none;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.left, .right { display: inline-flex; align-items: center; gap: 10px; flex-wrap: wrap; }

.brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.18em;
}

.cov {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-muted);
  letter-spacing: 0.1em;
}

.ok-pill, .warn-pill, .fail-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 8px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}
.ok-pill   { background: var(--ok-soft);     color: var(--ok); }
.warn-pill { background: var(--warn-soft);   color: var(--warn); }
.fail-pill { background: var(--danger-soft); color: var(--danger); }
.ok-pill .dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: currentColor;
  animation: pulse-soft 2s ease-in-out infinite;
}

.dod {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 9px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10.5px;
}
.dod.up   { color: var(--critical); background: var(--critical-soft); }
.dod.down { color: var(--ok);       background: var(--ok-soft); }
.dod.flat { color: var(--text-muted); background: var(--bg-elev-3); }

.refresh {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.refresh:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}
.refresh:disabled { opacity: 0.6; cursor: not-allowed; }
.spinning { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.narrative {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
  letter-spacing: 0.01em;
}

.narrative-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--accent);
}

.prompt-mark {
  color: var(--accent);
  font-weight: 600;
  flex-shrink: 0;
}

.thinking-text { color: var(--text-secondary); }

.caret {
  display: inline-block;
  width: 6px;
  height: 14px;
  background: var(--accent);
  animation: blink 1s steps(2) infinite;
}

/* Snapshot grid */
.snap-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}
@media (max-width: 900px) {
  .snap-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

.snap-cell {
  display: grid;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
}

.snap-cell .lbl {
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.snap-cell .val {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.snap-cell.critical .val { color: var(--critical); }
.snap-cell.ok .val { color: var(--ok); }
.snap-cell.warn .val { color: var(--warn); }

/* Highlights */
.highlights { display: grid; gap: 8px; }

.hl-eyebrow {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.hl-list { display: grid; gap: 4px; }

.hl-row {
  display: grid;
  grid-template-columns: 3px 1fr;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  cursor: pointer;
  transition: all 0.12s ease;
}
.hl-row:hover {
  border-color: var(--accent-line);
  background: var(--bg-elev-1);
}

.hl-bar { align-self: stretch; border-radius: 3px; }

.hl-meta { display: grid; gap: 4px; min-width: 0; }

.hl-row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hl-title {
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
  white-space: nowrap;
}

.hl-row2 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.hl-row2 .sep { color: var(--text-faint); }

.st {
  padding: 0 6px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
}
.st.pending   { background: var(--warn-soft);    color: var(--warn); }
.st.confirmed { background: var(--accent-soft);  color: var(--accent); }
.st.recovered { background: var(--ok-soft);      color: var(--ok); }
.st.closed    { background: var(--bg-elev-3);    color: var(--text-muted); }
</style>
