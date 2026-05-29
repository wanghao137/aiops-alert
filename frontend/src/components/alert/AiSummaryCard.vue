<template>
  <div class="ai-summary-card" :class="status">
    <header class="head">
      <div class="brand">
        <Sparkles :size="11" :stroke-width="1.8" />
        <span>AI 智能分析</span>
      </div>
      <span v-if="status === 'success'" class="ready-pill">
        <span class="ready-dot" />
        已生成
      </span>
      <span v-else-if="status === 'pending' || loading" class="ready-pill thinking">
        <span class="spinner" />
        生成中
      </span>
      <span v-else-if="status === 'stale'" class="ready-pill stale">
        <AlertTriangle :size="11" :stroke-width="1.8" />
        未完成
      </span>
      <button v-if="!loading" class="refresh-btn" :class="{ subtle: status === 'pending' || status === 'stale' }" @click="emit('refresh')">
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
      <div class="thinking-line">
        <span class="prompt-mark">▸</span>
        <span class="thinking-text">{{ thinkingText }}</span>
        <span class="caret" />
      </div>
      <LiveThinkingStream
        :active="loading || status === 'pending'"
        scene="event-summary"
        compact
      />
      <div v-if="status === 'pending' && !loading" class="pending-hint">
        摘要等待回写中。若长期停留在该状态，可重新生成。
      </div>
    </div>

    <!-- PENDING 过期：不再伪装成正在思考 -->
    <div v-else-if="status === 'stale'" class="stale-state">
      <div class="stale-icon">
        <AlertTriangle :size="15" :stroke-width="1.8" />
      </div>
      <div class="stale-copy">
        <strong>摘要未完成</strong>
        <span>上一次生成没有回写结果，可能是服务重启、模型超时或任务中断。请重新生成。</span>
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  Sparkles, RefreshCw, AlertTriangle,
  Search, Activity, HelpCircle, CheckCircle2
} from 'lucide-vue-next'
import { parseAiSummary, type AiSummary } from '@/api/alertEvent'
import LiveThinkingStream from '@/components/ai/LiveThinkingStream.vue'

const props = defineProps<{
  rawSummary?: string
  rawStatus?: string
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'refresh'): void }>()

const summary = computed<AiSummary | undefined>(() => parseAiSummary(props.rawSummary))

const status = computed(() => {
  if (props.loading) return 'loading'
  if (props.rawStatus === 'STALE') return 'stale'
  if (props.rawStatus === 'PENDING' || (!props.rawStatus && !props.rawSummary)) return 'pending'
  if (props.rawStatus === 'FAILED') return 'failed'
  return 'success'
})

// 终端打字机：PENDING 状态下循环展示 4 段消息，制造 AI 思考的感觉。
const THINKING_MESSAGES = [
  '读取告警上下文…',
  '比对历史相似事件…',
  '分析根因路径…',
  '生成处置建议…'
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
  }, 1600)
}

function stopThinking() {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = undefined
  }
}

watch(status, (s) => {
  if (s === 'pending' || s === 'loading') {
    startThinking()
  } else {
    stopThinking()
  }
}, { immediate: true })

onBeforeUnmount(() => stopThinking())
</script>

<style scoped>
.ai-summary-card {
  position: relative;
  display: grid;
  gap: 16px;
  align-self: start;
  width: 100%;
  min-width: 0;
  height: auto;
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--accent-line);
  background:
    linear-gradient(135deg, var(--accent-soft) 0%, transparent 60%),
    var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
  isolation: isolate;
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
  display: grid;
  grid-template-columns: max-content max-content minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  margin-bottom: 0;
  min-width: 0;
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
  white-space: nowrap;
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

.ready-pill.stale {
  background: var(--warn-soft);
  color: var(--warn);
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
  justify-self: end;
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

.refresh-btn.subtle {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent);
}

/* ========== Loading skeleton ========== */
.loading {
  display: grid;
  gap: 12px;
}

.loading-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.loading-cell {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  min-width: 0;
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

.thinking-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--accent-line);
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--accent);
  letter-spacing: 0.02em;
  align-self: flex-start;
}

.thinking-line .prompt-mark {
  color: var(--accent);
  font-weight: 600;
}

.thinking-line .thinking-text {
  color: var(--text-secondary);
}

.thinking-line .caret {
  display: inline-block;
  width: 6px;
  height: 14px;
  background: var(--accent);
  animation: blink 1s steps(2) infinite;
}

.pending-hint {
  width: 100%;
  padding: 9px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.stale-state {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid rgba(251, 191, 36, 0.36);
  border-radius: var(--radius-sm);
  background: var(--warn-soft);
  color: var(--warn);
  min-width: 0;
}

.stale-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid rgba(251, 191, 36, 0.42);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--warn) 10%, transparent);
}

.stale-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.stale-copy strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
}

.stale-copy span {
  color: var(--text-secondary);
  font-size: 12.5px;
  line-height: 1.7;
  overflow-wrap: anywhere;
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
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.block {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-left: 2px solid;
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
  min-width: 0;
  overflow: hidden;
  transition: all 0.15s ease;
}

.block:hover {
  border-color: var(--line-strong);
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
  overflow-wrap: anywhere;
  word-break: break-word;
}

.block-text.muted { color: var(--text-muted); }

.block-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.block-list li + li { margin-top: 4px; }

/* Responsive */
@media (max-width: 640px) {
  .head {
    grid-template-columns: 1fr auto;
  }

  .refresh-btn {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .stale-state {
    grid-template-columns: 1fr;
  }
}
</style>
