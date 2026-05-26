<template>
  <div class="ai-summary-card" :class="status">
    <header class="head">
      <div class="brand">
        <Sparkles :size="11" :stroke-width="1.8" />
        <span>AI INTELLIGENCE</span>
      </div>
      <span v-if="status === 'success'" class="ready-pill">
        <span class="ready-dot" />
        已生成
      </span>
      <span v-else-if="status === 'pending' || loading" class="ready-pill thinking">
        <span class="spinner" />
        生成中
      </span>
      <button v-if="!loading && status !== 'pending'" class="refresh-btn" @click="emit('refresh')">
        <RefreshCw :size="12" :stroke-width="1.8" />
        重新生成
      </button>
    </header>

    <!-- Loading 骨架 -->
    <div v-if="loading || status === 'pending'" class="loading">
      <div class="loading-grid">
        <div v-for="i in 4" :key="i" class="loading-cell">
          <div class="loading-icon" />
          <div class="loading-lines">
            <div class="skeleton" />
            <div class="skeleton w80" />
            <div class="skeleton w60" />
          </div>
        </div>
      </div>
      <div class="loading-text">
        <span class="dot-anim" />
        AI 正在分析这次告警…
      </div>
    </div>

    <!-- 失败 -->
    <div v-else-if="status === 'failed'" class="failed">
      <AlertTriangle :size="13" :stroke-width="1.8" />
      <span>{{ summary?.error || 'AI 摘要生成失败' }}</span>
    </div>

    <!-- 成功：4 段 grid -->
    <div v-else class="content">
      <article class="block what">
        <div class="block-head">
          <span class="block-icon"><Search :size="13" :stroke-width="1.7" /></span>
          <span class="block-title">发生了什么</span>
        </div>
        <div class="block-text">{{ summary?.what || '—' }}</div>
      </article>

      <article class="block impact">
        <div class="block-head">
          <span class="block-icon"><Activity :size="13" :stroke-width="1.7" /></span>
          <span class="block-title">影响范围</span>
        </div>
        <div class="block-text">{{ summary?.impact || '—' }}</div>
      </article>

      <article class="block causes">
        <div class="block-head">
          <span class="block-icon"><HelpCircle :size="13" :stroke-width="1.7" /></span>
          <span class="block-title">可能原因</span>
        </div>
        <ul v-if="summary?.causes?.length" class="block-list">
          <li v-for="(c, i) in summary.causes" :key="i">{{ c }}</li>
        </ul>
        <div v-else class="block-text muted">—</div>
      </article>

      <article class="block actions">
        <div class="block-head">
          <span class="block-icon"><CheckCircle2 :size="13" :stroke-width="1.7" /></span>
          <span class="block-title">建议动作</span>
        </div>
        <ul v-if="summary?.actions?.length" class="block-list">
          <li v-for="(a, i) in summary.actions" :key="i">{{ a }}</li>
        </ul>
        <div v-else class="block-text muted">—</div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Sparkles, RefreshCw, AlertTriangle,
  Search, Activity, HelpCircle, CheckCircle2
} from 'lucide-vue-next'
import { parseAiSummary, type AiSummary } from '@/api/alertEvent'

const props = defineProps<{
  rawSummary?: string
  rawStatus?: string
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'refresh'): void }>()

const summary = computed<AiSummary | undefined>(() => parseAiSummary(props.rawSummary))

const status = computed(() => {
  if (props.loading) return 'loading'
  if (props.rawStatus === 'PENDING' || (!props.rawStatus && !props.rawSummary)) return 'pending'
  if (props.rawStatus === 'FAILED') return 'failed'
  return 'success'
})
</script>

<style scoped>
.ai-summary-card {
  position: relative;
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--accent-line);
  background:
    linear-gradient(135deg, var(--accent-soft) 0%, transparent 60%),
    var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
}

.ai-summary-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  background-size: 200% 100%;
  animation: shimmer 6s linear infinite;
  pointer-events: none;
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

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

.ready-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--ok-soft);
  color: var(--ok);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.ready-pill.thinking {
  background: var(--accent-soft);
  color: var(--accent);
}

.ready-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-soft 2s ease-in-out infinite;
}

.spinner {
  width: 9px;
  height: 9px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.refresh-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

/* ========== Loading skeleton ========== */
.loading {
  display: grid;
  gap: 12px;
}

.loading-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.loading-cell {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
}

.loading-icon {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--bg-elev-3), var(--line-strong), var(--bg-elev-3));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
  flex-shrink: 0;
}

.loading-lines { flex: 1; display: grid; gap: 5px; min-width: 0; }

.skeleton {
  height: 8px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--bg-elev-3), var(--line-strong), var(--bg-elev-3));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}

.skeleton.w80 { width: 80%; }
.skeleton.w60 { width: 60%; }

@keyframes skel {
  0%   { background-position: -100% 0; }
  100% { background-position:  100% 0; }
}

.loading-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.dot-anim {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  animation: blink 1s steps(2) infinite;
}

/* ========== Failed ========== */
.failed {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid rgba(248, 113, 113, 0.35);
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
}

/* ========== Content (4 段 grid) ========== */
.content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.block {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-left: 2px solid;
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
  transition: all 0.15s ease;
}

.block:hover {
  border-color: var(--line-strong);
  transform: translateY(-1px);
}

.block.what    { border-left-color: var(--accent); }
.block.impact  { border-left-color: var(--warn); }
.block.causes  { border-left-color: var(--danger); }
.block.actions { border-left-color: var(--ok); }

.block-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.block-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.block.what    .block-icon { background: var(--accent-soft);  color: var(--accent); }
.block.impact  .block-icon { background: var(--warn-soft);    color: var(--warn); }
.block.causes  .block-icon { background: var(--danger-soft);  color: var(--danger); }
.block.actions .block-icon { background: var(--ok-soft);      color: var(--ok); }

.block-title {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.block.what    .block-title { color: var(--accent); }
.block.impact  .block-title { color: var(--warn); }
.block.causes  .block-title { color: var(--danger); }
.block.actions .block-title { color: var(--ok); }

.block-text {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
}

.block-text.muted { color: var(--text-muted); }

.block-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
}

.block-list li + li { margin-top: 4px; }

/* Responsive */
@media (max-width: 640px) {
  .content,
  .loading-grid {
    grid-template-columns: 1fr;
  }
}
</style>
