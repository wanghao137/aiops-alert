<template>
  <span class="tabular-nums">{{ display }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    duration?: number
    format?: (n: number) => string
  }>(),
  { duration: 600 }
)

const display = ref(formatValue(props.value))

watch(
  () => props.value,
  (next, prev) => {
    if (next === prev) return
    const start = Number(prev) || 0
    const end = Number(next) || 0
    const startTime = performance.now()
    const dur = Math.max(120, props.duration)

    function tick(now: number) {
      const t = Math.min(1, (now - startTime) / dur)
      // easeOutCubic
      const eased = 1 - Math.pow(1 - t, 3)
      const v = Math.round(start + (end - start) * eased)
      display.value = formatValue(v)
      if (t < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }
)

function formatValue(n: number) {
  return props.format ? props.format(n) : (n ?? 0).toLocaleString('zh-CN')
}
</script>
