<template>
  <div class="ai-summary-card" :class="status">
    <header class="head">
      <div class="brand">
        <Sparkles :size="14" />
        <span>AI 智能分析</span>
      </div>
      <button v-if="!loading && status !== 'pending'" class="refresh-btn" @click="emit('refresh')">
        <RefreshCw :size="13" />
        重新生成
      </button>
    </header>

    <!-- Loading 骨架 -->
    <div v-if="loading || status === 'pending'" class="loading">
      <div class="skeleton" v-for="i in 4" :key="i" />
      <div class="loading-text">
        <span class="dot-anim" />
        AI 正在分析这次告警...
      </div>
    </div>

    <!-- 失败 -->
    <div v-else-if="status === 'failed'" class="failed">
      <AlertTriangle :size="14" />
      <span>{{ summary?.error || 'AI 摘要生成失败' }}</span>
    </div>

    <!-- 成功 -->
    <div v-else class="content">
      <section class="block">
        <div class="block-icon what"><Search :size="14" /></div>
        <div>
          <div class="block-title">发生了什么</div>
          <div class="block-text">{{ summary?.what || '-' }}</div>
        </div>
      </section>
      <section class="block">
        <div class="block-icon impact"><Activity :size="14" /></div>
        <div>
          <div class="block-title">影响范围</div>
          <div class="block-text">{{ summary?.impact || '-' }}</div>
        </div>
      </section>
      <section class="block">
        <div class="block-icon causes"><HelpCircle :size="14" /></div>
        <div>
          <div class="block-title">可能原因</div>
          <ul v-if="summary?.causes?.length">
            <li v-for="(c, i) in summary.causes" :key="i">{{ c }}</li>
          </ul>
          <div v-else class="block-text muted">-</div>
        </div>
      </section>
      <section class="block">
        <div class="block-icon actions"><CheckCircle2 :size="14" /></div>
        <div>
          <div class="block-title">建议动作</div>
          <ul v-if="summary?.actions?.length">
            <li v-for="(a, i) in summary.actions" :key="i">{{ a }}</li>
          </ul>
          <div v-else class="block-text muted">-</div>
        </div>
      </section>
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
  padding: 16px;
  border-radius: 12px;
  border: 1px solid rgba(139, 92, 246, 0.3);
  background:
    linear-gradient(135deg, rgba(59, 130, 246, 0.08) 0%, rgba(139, 92, 246, 0.06) 100%),
    var(--bg-panel);
  overflow: hidden;
}

.ai-summary-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 30%, rgba(139, 92, 246, 0.06) 50%, transparent 70%);
  background-size: 200% 100%;
  animation: shimmer 6s linear infinite;
  pointer-events: none;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, #3B82F6, #8B5CF6);
  color: white;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.refresh-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.loading { display: grid; gap: 8px; padding: 4px 0; }

.skeleton {
  height: 10px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--bg-subtle), var(--bg-hover), var(--bg-subtle));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}

.skeleton:nth-child(2) { width: 90%; }
.skeleton:nth-child(3) { width: 75%; }
.skeleton:nth-child(4) { width: 60%; }

@keyframes skel {
  0%   { background-position: -100% 0; }
  100% { background-position:  100% 0; }
}

.loading-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.dot-anim {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  animation: blink 1.2s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 0.3; }
  50%      { opacity: 1; }
}

.failed {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #FCA5A5;
  font-size: 13px;
}

.content { display: grid; gap: 12px; }

.block {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  align-items: flex-start;
}

.block-icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  flex-shrink: 0;
  margin-top: 2px;
}

.block-icon.what    { background: rgba(59, 130, 246, 0.18); color: #93C5FD; }
.block-icon.impact  { background: rgba(245, 158, 11, 0.18); color: #FCD34D; }
.block-icon.causes  { background: rgba(139, 92, 246, 0.18); color: #C4B5FD; }
.block-icon.actions { background: rgba(16, 185, 129, 0.18); color: #6EE7B7; }

.block-title {
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0.4px;
  margin-bottom: 3px;
}

.block-text {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
}

.block-text.muted { color: var(--text-muted); }

ul {
  margin: 0;
  padding-left: 18px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
}
</style>
