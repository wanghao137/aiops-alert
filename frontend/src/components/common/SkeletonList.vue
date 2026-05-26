<template>
  <div class="skel-list" :class="`variant-${variant}`">
    <div v-for="i in rows" :key="i" class="skel-row">
      <span class="skel-bar" />
      <div class="skel-body">
        <div class="skel-line w70" />
        <div class="skel-line w40" />
        <div v-if="variant === 'card'" class="skel-line w90" />
      </div>
      <div v-if="variant === 'row'" class="skel-num" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  rows?: number
  variant?: 'card' | 'row'
}>(), {
  rows: 6,
  variant: 'card'
})
</script>

<style scoped>
.skel-list {
  display: grid;
  gap: 8px;
}

.skel-row {
  display: grid;
  grid-template-columns: 3px 1fr;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  animation: fade-up 0.3s ease both;
}

.skel-list.variant-row .skel-row {
  grid-template-columns: 3px 1fr 60px;
  padding: 10px 14px;
}

.skel-bar {
  align-self: stretch;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--bg-elev-2), var(--line-strong), var(--bg-elev-2));
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s linear infinite;
}

.skel-body {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.skel-line {
  height: 10px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--bg-elev-2), var(--line-strong), var(--bg-elev-2));
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s linear infinite;
}

.skel-line.w40 { width: 40%; }
.skel-line.w70 { width: 70%; }
.skel-line.w90 { width: 90%; }

.skel-num {
  width: 60px;
  height: 32px;
  align-self: center;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--bg-elev-2), var(--line-strong), var(--bg-elev-2));
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s linear infinite;
}

@keyframes skel-shimmer {
  0%   { background-position: -100% 0; }
  100% { background-position:  100% 0; }
}
</style>
