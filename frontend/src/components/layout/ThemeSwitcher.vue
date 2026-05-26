<template>
  <div class="theme-switcher" role="group" aria-label="主题切换">
    <button
      v-for="opt in options"
      :key="opt.value"
      class="seg"
      :class="{ active: store.mode === opt.value }"
      :title="opt.title"
      type="button"
      @click="store.setMode(opt.value)"
    >
      <component :is="opt.icon" :size="13" :stroke-width="1.6" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { Sun, Moon, Monitor } from 'lucide-vue-next'
import { useThemeStore, type ThemeMode } from '@/stores/theme'

const store = useThemeStore()

const options: { value: ThemeMode; title: string; icon: unknown }[] = [
  { value: 'light', title: '浅色', icon: Sun },
  { value: 'dark', title: '深色', icon: Moon },
  { value: 'system', title: '跟随系统', icon: Monitor }
]
</script>

<style scoped>
.theme-switcher {
  display: inline-flex;
  align-items: center;
  padding: 2px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
}

.seg {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.seg:hover {
  color: var(--text-primary);
}

.seg.active {
  background: var(--accent-soft);
  color: var(--accent);
}
</style>
