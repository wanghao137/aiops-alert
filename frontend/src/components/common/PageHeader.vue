<template>
  <section class="page-header">
    <div class="title-block">
      <div v-if="eyebrow" class="eyebrow">{{ eyebrow }}</div>
      <h2>{{ title }}</h2>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
    </div>
    <div class="actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  eyebrow?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 22px 24px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background:
    radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.10), transparent 60%),
    radial-gradient(circle at 100% 100%, rgba(139, 92, 246, 0.10), transparent 60%),
    var(--bg-panel);
}

.eyebrow {
  display: inline-block;
  margin-bottom: 6px;
  padding: 2px 10px;
  border: 1px solid rgba(59, 130, 246, 0.5);
  border-radius: 999px;
  color: #93C5FD;
  font-size: 11px;
  letter-spacing: 1.4px;
}

h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 600;
}

.subtitle {
  margin: 4px 0 0;
  max-width: 720px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
