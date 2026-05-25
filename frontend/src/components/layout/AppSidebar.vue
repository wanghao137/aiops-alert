<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">
        <Sparkles :size="18" />
      </div>
      <div class="brand-text">
        <div class="brand-title">AIOps Alert</div>
        <div class="brand-sub">智能监控告警</div>
      </div>
    </div>

    <nav class="nav">
      <router-link
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        active-class="active"
      >
        <component :is="iconOf(item.icon)" :size="18" />
        <span>{{ item.title }}</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <button class="hint" @click="emit('open-command')">
        <span class="kbd">⌘ K</span>
        <span>唤起命令面板</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Sparkles,
  LayoutDashboard,
  BellRing,
  Flame,
  Server,
  Send,
  Settings
} from 'lucide-vue-next'

const emit = defineEmits<{ (e: 'open-command'): void }>()

const router = useRouter()
const items = computed(() =>
  router.options.routes
    .filter((r) => r.meta?.title)
    .map((r) => ({
      path: r.path,
      title: r.meta?.title as string,
      icon: r.meta?.icon as string
    }))
)

const iconMap: Record<string, unknown> = {
  LayoutDashboard,
  BellRing,
  Flame,
  Sparkles,
  Server,
  Send,
  Settings
}

function iconOf(name: string) {
  return iconMap[name] || Sparkles
}
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, #0F172A 0%, #0B1120 100%);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--line);
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: linear-gradient(135deg, #3B82F6, #8B5CF6);
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.brand-title {
  color: var(--text-primary);
  font-weight: 700;
  letter-spacing: 0.4px;
}

.brand-sub {
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.nav {
  flex: 1;
  padding: 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 13px;
  transition: all 0.15s ease;
}

.nav-item:hover {
  color: var(--text-primary);
  background: var(--bg-subtle);
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.18) 0%, rgba(59, 130, 246, 0) 100%);
  color: var(--text-primary);
  box-shadow: inset 2px 0 0 var(--accent);
}

.sidebar-footer {
  padding: 12px 16px 18px;
  border-top: 1px solid var(--line);
}

.hint {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: color 0.15s ease;
}

.hint:hover {
  color: var(--text-primary);
}

.hint:hover .kbd {
  border-color: var(--accent);
  color: var(--accent);
}

.kbd {
  padding: 2px 7px;
  border: 1px solid var(--line-subtle);
  border-radius: 6px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}
</style>
