<template>
  <div class="stat-card" :style="cardStyle">
    <div class="stat-icon" :style="iconStyle">
      <component :is="icon" :size="20" />
    </div>
    <div class="stat-body">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value tabular-nums">
        {{ formatted }}<span v-if="suffix" class="stat-suffix">{{ suffix }}</span>
      </div>
      <div v-if="hint" class="stat-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

const props = defineProps<{
  label: string
  value: number
  suffix?: string
  hint?: string
  icon: Component
  accent?: string // 主色，例如 #3B82F6
}>()

const accent = computed(() => props.accent || '#3B82F6')

const cardStyle = computed(() => ({
  background: `linear-gradient(135deg, ${hexToRgba(accent.value, 0.10)} 0%, transparent 60%), var(--bg-panel)`,
  borderColor: hexToRgba(accent.value, 0.25)
}))

const iconStyle = computed(() => ({
  background: hexToRgba(accent.value, 0.18),
  color: accent.value
}))

const formatted = computed(() => {
  const v = props.value ?? 0
  return v.toLocaleString('zh-CN')
})

function hexToRgba(hex: string, alpha: number) {
  const m = hex.replace('#', '').match(/.{1,2}/g)
  if (!m) return hex
  const [r, g, b] = m.map((x) => parseInt(x, 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: 12px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px -16px rgba(0, 0, 0, 0.5);
}

.stat-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.stat-label {
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.4px;
}

.stat-value {
  color: var(--text-primary);
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
}

.stat-suffix {
  margin-left: 4px;
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 400;
}

.stat-hint {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
