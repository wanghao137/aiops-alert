<template>
  <span class="level-badge" :style="style">
    <span class="dot" :style="{ background: meta.color }" />
    <span>{{ meta.label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { levelMeta } from '@/utils/level'

const props = defineProps<{ level?: string; size?: 'sm' | 'md' }>()
const meta = computed(() => levelMeta(props.level))
const style = computed(() => ({
  background: meta.value.bg,
  border: `1px solid ${meta.value.border}`,
  color: meta.value.text,
  padding: props.size === 'sm' ? '2px 8px' : '3px 10px',
  fontSize: props.size === 'sm' ? '11px' : '12px'
}))
</script>

<style scoped>
.level-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  font-weight: 500;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
</style>
