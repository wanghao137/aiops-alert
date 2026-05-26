<template>
  <div v-if="content" class="thinking-panel" :class="{ collapsed: !expanded }">
    <button class="toggle" type="button" @click="expanded = !expanded">
      <Brain :size="14" class="brain-icon" :class="{ pulse: !expanded }" />
      <span class="title">{{ title || '模型思考过程' }}</span>
      <span class="meta">{{ wordCount }} 字 · {{ elapsed }}</span>
      <ChevronDown v-if="expanded" :size="14" class="chev" />
      <ChevronRight v-else :size="14" class="chev" />
    </button>

    <transition name="thinking">
      <div v-if="expanded" class="body">
        <pre class="text">{{ content }}</pre>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Brain, ChevronDown, ChevronRight } from 'lucide-vue-next'

const props = defineProps<{
  content?: string
  title?: string
  durationMs?: number
  /** 默认是否展开 */
  defaultExpanded?: boolean
}>()

const expanded = ref(!!props.defaultExpanded)

const wordCount = computed(() => (props.content || '').length)

const elapsed = computed(() => {
  if (!props.durationMs) return ''
  if (props.durationMs < 1000) return `${props.durationMs}ms`
  return `${(props.durationMs / 1000).toFixed(1)}s`
})
</script>

<style scoped>
.thinking-panel {
  border: 1px solid rgba(139, 92, 246, 0.25);
  border-radius: 10px;
  background:
    linear-gradient(135deg, rgba(139, 92, 246, 0.06), rgba(59, 130, 246, 0.04)),
    var(--bg-subtle);
  overflow: hidden;
  transition: border-color 0.15s ease;
}

.thinking-panel:hover {
  border-color: rgba(139, 92, 246, 0.4);
}

.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
}

.toggle:hover { color: var(--text-primary); }

.brain-icon { color: #C4B5FD; flex-shrink: 0; }

.brain-icon.pulse {
  animation: brain-pulse 2.4s ease-in-out infinite;
}

@keyframes brain-pulse {
  0%, 100% { opacity: 0.7; transform: scale(1); }
  50%      { opacity: 1;   transform: scale(1.08); }
}

.title {
  color: var(--text-primary);
  font-weight: 500;
}

.meta {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.chev {
  color: var(--text-muted);
  flex-shrink: 0;
}

.body {
  padding: 0 12px 12px;
  border-top: 1px dashed rgba(139, 92, 246, 0.18);
}

.text {
  margin: 10px 0 0;
  max-height: 280px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--bg-base);
  color: #C4B5FD;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.text::-webkit-scrollbar { width: 5px; }
.text::-webkit-scrollbar-thumb { background: #2a3447; border-radius: 3px; }

.thinking-enter-active,
.thinking-leave-active {
  transition: opacity 0.2s ease, max-height 0.3s ease;
  overflow: hidden;
}

.thinking-enter-from,
.thinking-leave-to {
  opacity: 0;
  max-height: 0;
}

.thinking-enter-to,
.thinking-leave-from {
  opacity: 1;
  max-height: 400px;
}
</style>
