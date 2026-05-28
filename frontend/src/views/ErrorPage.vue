<template>
  <div class="error-page" :class="`variant-${variant}`">
    <div class="terminal-shell">
      <div class="term-head">
        <span class="term-dot r" />
        <span class="term-dot y" />
        <span class="term-dot g" />
        <span class="term-name">aiops:error</span>
        <span class="term-status">
          <span class="dot-anim" />
          {{ variant === '404' ? '未找到 NOT-FOUND' : '内部错误 INTERNAL-ERROR' }}
        </span>
      </div>
      <div class="term-body">
        <div class="big-num">{{ variant }}</div>
        <div class="hint">{{ resolvedMessage }}</div>
        <div class="trace">
          <span class="prompt-mark">▸</span>
          <span class="trace-text">{{ traceText }}</span>
        </div>

        <div class="actions">
          <button v-if="variant === '404'" class="btn primary" @click="goHome">
            <ArrowLeft :size="13" :stroke-width="1.8" />
            返回总览大屏
          </button>
          <button v-else class="btn primary" @click="onRetryClick">
            <RefreshCw :size="13" :stroke-width="1.8" />
            重试
          </button>
          <button class="btn ghost" @click="goBack">
            <ChevronLeft :size="13" :stroke-width="1.8" />
            上一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChevronLeft, RefreshCw } from 'lucide-vue-next'

const props = defineProps<{
  variant: '404' | '500'
  message?: string
  onRetry?: () => void
}>()

const router = useRouter()

const resolvedMessage = computed(() => {
  if (props.message) return props.message
  return props.variant === '404'
    ? '此路径不存在或已被移除'
    : '后端接口暂时不可用，请稍后再试或点击重试'
})

const traceText = computed(() => {
  if (props.variant === '404') {
    return '路由解析失败：没有匹配页面'
  }
  return '后端接口返回 500 或更高状态码'
})

function goHome() {
  router.push('/dashboard')
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    goHome()
  }
}

function onRetryClick() {
  props.onRetry?.()
}
</script>

<style scoped>
.error-page {
  display: grid;
  place-items: center;
  padding: 64px 28px;
  min-height: 60vh;
  animation: fade-up 0.35s ease both;
}

.terminal-shell {
  width: min(640px, 100%);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-lg);
  background: var(--bg-elev-1);
  box-shadow: var(--inset), 0 24px 64px -12px rgba(0, 0, 0, 0.45);
  overflow: hidden;
}

.term-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  background: var(--bg-elev-3);
  border-bottom: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 11px;
}

.term-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.term-dot.r { background: var(--critical); }
.term-dot.y { background: var(--warn); }
.term-dot.g { background: var(--ok); }

.term-name {
  margin-left: 8px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.term-status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--bg-elev-1);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.error-page.variant-404 .term-status { color: var(--accent); }
.error-page.variant-500 .term-status { color: var(--critical); }

.dot-anim {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.term-body {
  display: grid;
  gap: 14px;
  padding: 32px 36px 28px;
}

.big-num {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 96px;
  line-height: 0.85;
  letter-spacing: -0.05em;
}

.error-page.variant-404 .big-num { color: var(--accent); }
.error-page.variant-500 .big-num { color: var(--critical); }

.hint {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.trace {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  width: max-content;
  max-width: 100%;
}

.error-page.variant-500 .trace { border-left-color: var(--critical); }

.prompt-mark {
  color: var(--accent);
  font-weight: 600;
}

.error-page.variant-500 .prompt-mark { color: var(--critical); }

.actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--bg-base);
  font-weight: 500;
}

.error-page.variant-500 .btn.primary {
  background: var(--critical);
  border-color: var(--critical);
}

.btn.primary:hover { filter: brightness(1.08); }

.btn.ghost:hover {
  border-color: var(--accent-line);
  color: var(--accent);
}
</style>
