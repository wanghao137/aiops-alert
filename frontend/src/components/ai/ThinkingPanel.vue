<template>
  <div v-if="content" class="thinking-panel" :class="{ collapsed: !expanded }">
    <button class="toggle" type="button" @click="expanded = !expanded">
      <span class="brain-wrap" :class="{ pulse: !expanded }">
        <Brain :size="13" :stroke-width="1.7" />
      </span>
      <span class="title">{{ title || '模型思考过程' }}</span>
      <span class="meta">
        <span class="meta-num tabular-nums">{{ wordCount }}</span> 字
        <span v-if="elapsed" class="meta-sep">·</span>
        <span v-if="elapsed" class="meta-num tabular-nums">{{ elapsed }}</span>
      </span>
      <ChevronDown v-if="expanded" :size="13" :stroke-width="1.8" class="chev" />
      <ChevronRight v-else :size="13" :stroke-width="1.8" class="chev" />
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
  border: 1px solid var(--accent-line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-md);
  background: var(--accent-soft);
  overflow: hidden;
  transition: all 0.18s ease;
}

.thinking-panel:hover {
  border-color: var(--accent);
}

.toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 14px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
}

.toggle:hover { color: var(--text-primary); }

.brain-wrap {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--accent-line);
  background: var(--bg-elev-1);
  color: var(--accent);
  flex-shrink: 0;
}

.brain-wrap.pulse {
  animation: brain-pulse 2.4s ease-in-out infinite;
}

@keyframes brain-pulse {
  0%, 100% { opacity: 0.7; box-shadow: 0 0 0 0 rgba(125, 211, 252, 0); }
  50%      { opacity: 1;   box-shadow: 0 0 0 4px rgba(125, 211, 252, 0.10); }
}

.title {
  font-family: var(--font-display);
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.meta {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.meta-num { color: var(--text-secondary); }
.meta-sep { color: var(--text-faint); }

.chev {
  color: var(--text-muted);
  flex-shrink: 0;
}

.body {
  padding: 0 14px 12px;
  border-top: 1px dashed var(--accent-line);
}

.text {
  margin: 10px 0 0;
  max-height: 280px;
  overflow: auto;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.text::-webkit-scrollbar { width: 5px; }
.text::-webkit-scrollbar-thumb { background: var(--line-strong); border-radius: 3px; }

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
