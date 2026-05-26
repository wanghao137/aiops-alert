<template>
  <transition name="banner-slide">
    <div v-if="visible" class="net-banner" :class="severity">
      <span class="dot" />
      <div class="msg">
        <strong>{{ title }}</strong>
        <span class="hint">{{ hint }}</span>
      </div>
      <button class="reconnect" :disabled="reconnecting" @click="onReconnectClick">
        <RefreshCw :size="12" :stroke-width="1.8" :class="{ spinning: reconnecting }" />
        {{ reconnecting ? '重连中…' : '重新连接' }}
      </button>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { useHttpHealth } from '@/composables/useHttpHealth'

const props = defineProps<{
  sseConnected: boolean
}>()

const emit = defineEmits<{ (e: 'reconnect'): void }>()

const { unhealthy, reset: resetHttpHealth } = useHttpHealth()

/** SSE 断开持续 ≥ 30 秒后才显示，避免短暂抖动刷屏。 */
const DISCONNECT_GRACE_MS = 30_000
/** 恢复后保留 3 秒"已重连"提示再隐藏。 */
const RECOVER_LINGER_MS = 3_000

const sseLost = ref(false)            // SSE 已确认断开（30 秒后置位）
const reconnecting = ref(false)
let lostTimer: ReturnType<typeof setTimeout> | undefined
let recoverTimer: ReturnType<typeof setTimeout> | undefined

watch(() => props.sseConnected, (connected) => {
  if (connected) {
    if (lostTimer) {
      clearTimeout(lostTimer)
      lostTimer = undefined
    }
    if (sseLost.value) {
      // 之前已显示，保留 3s 后清除
      if (recoverTimer) clearTimeout(recoverTimer)
      recoverTimer = setTimeout(() => {
        sseLost.value = false
        reconnecting.value = false
      }, RECOVER_LINGER_MS)
    } else {
      reconnecting.value = false
    }
  } else {
    if (lostTimer) clearTimeout(lostTimer)
    lostTimer = setTimeout(() => {
      sseLost.value = true
    }, DISCONNECT_GRACE_MS)
  }
}, { immediate: true })

const visible = computed(() => sseLost.value || unhealthy.value)

const severity = computed<'warn' | 'danger'>(() => unhealthy.value ? 'danger' : 'warn')

const title = computed(() => {
  if (unhealthy.value) return '后端接口异常'
  return '实时连接已断开'
})

const hint = computed(() => {
  if (unhealthy.value) return '5 分钟内多次返回 5xx，告警可能漏推 · 请检查后端'
  return '已超过 30 秒未收到事件流，告警可能漏推'
})

function onReconnectClick() {
  reconnecting.value = true
  resetHttpHealth()
  emit('reconnect')
  // 兜底：5 秒后若仍未恢复，让按钮可再次点击
  setTimeout(() => {
    if (reconnecting.value) reconnecting.value = false
  }, 5_000)
}
</script>

<style scoped>
.net-banner {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 24px;
  border-bottom: 1px solid;
  font-family: var(--font-sans);
  font-size: 12.5px;
  backdrop-filter: blur(6px);
}

.net-banner.warn {
  background: var(--warn-soft);
  border-bottom-color: rgba(251, 191, 36, 0.35);
  color: var(--warn);
}

.net-banner.danger {
  background: var(--danger-soft);
  border-bottom-color: rgba(248, 113, 113, 0.35);
  color: var(--danger);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-soft 2s ease-in-out infinite;
  flex-shrink: 0;
}

.msg {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.msg strong {
  font-weight: 500;
  color: currentColor;
}

.msg .hint {
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.reconnect {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border: 1px solid currentColor;
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: currentColor;
  font-family: var(--font-sans);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.reconnect:hover:not(:disabled) {
  background: currentColor;
  color: var(--bg-base);
}

.reconnect:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.banner-slide-enter-active,
.banner-slide-leave-active {
  transition: transform 0.25s ease, opacity 0.2s ease;
  transform-origin: top;
}

.banner-slide-enter-from,
.banner-slide-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
