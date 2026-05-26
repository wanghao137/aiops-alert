<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 17l4-8 4 6 3-4 4 6 3-2" />
          <circle cx="20" cy="9" r="1.4" fill="currentColor" stroke="none" />
        </svg>
      </div>
      <div class="brand-text">
        <div class="brand-title">AIOPS<span>·</span>ALERT</div>
        <div class="brand-tag">智能监控告警 · v0.1</div>
      </div>
    </div>

    <div class="nav-section">
      <div class="nav-label">导航</div>
      <nav class="nav">
        <router-link
          v-for="item in items"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <component :is="iconOf(item.icon)" :size="16" :stroke-width="1.5" />
          <span>{{ item.title }}</span>
          <span v-if="item.badge" class="badge">{{ item.badge }}</span>
        </router-link>
      </nav>
    </div>

    <div class="sidebar-foot">
      <button class="cmd-trigger" @click="emit('open-command')">
        <span class="dot" />
        <span class="lbl">命令面板</span>
        <kbd>⌘ K</kbd>
      </button>
      <div class="version">
        <span>BUILD</span>
        <span class="ver">{{ buildId }}</span>
      </div>
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
  Settings,
  Activity
} from 'lucide-vue-next'

const emit = defineEmits<{ (e: 'open-command'): void }>()

const router = useRouter()
const items = computed(() =>
  router.options.routes
    .filter((r) => r.meta?.title)
    .map((r) => ({
      path: r.path,
      title: r.meta?.title as string,
      icon: r.meta?.icon as string,
      badge: r.meta?.badge as string | undefined
    }))
)

const buildId = computed(() => {
  // 简单生成一个稳定的 build id，实际上可以从 vite define 注入
  const d = new Date()
  return `${d.getFullYear() % 100}${(d.getMonth() + 1).toString().padStart(2, '0')}${d.getDate().toString().padStart(2, '0')}`
})

const iconMap: Record<string, unknown> = {
  LayoutDashboard, BellRing, Flame, Sparkles, Server, Send, Settings, Activity
}

function iconOf(name: string) {
  return iconMap[name] || Sparkles
}
</script>

<style scoped>
.sidebar {
  display: grid;
  grid-template-rows: auto 1fr auto;
  border-right: 1px solid var(--line);
  background: var(--bg-elev-1);
  height: 100vh;
  position: sticky;
  top: 0;
  overflow: hidden;
}

/* 品牌区 */
.brand {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  align-items: center;
  padding: 22px 22px 24px;
  border-bottom: 1px solid var(--line);
  position: relative;
}

.brand::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 22px;
  width: 28px;
  height: 1px;
  background: var(--accent);
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background:
    radial-gradient(circle at 30% 30%, rgba(125, 211, 252, 0.25), transparent 70%),
    var(--bg-elev-3);
  color: var(--accent);
  border: 1px solid var(--accent-line);
}

.brand-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 14px;
  letter-spacing: 0.08em;
  color: var(--text-primary);
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.brand-title span {
  color: var(--accent);
  margin: 0 1px;
}

.brand-tag {
  margin-top: 2px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}

/* 导航 */
.nav-section {
  padding: 18px 12px 0;
  overflow: auto;
}

.nav-label {
  padding: 0 10px 10px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.nav-item {
  position: relative;
  display: grid;
  grid-template-columns: 16px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  text-decoration: none;
  font-size: 13px;
  font-weight: 400;
  transition: all 0.12s ease;
}

.nav-item:hover {
  color: var(--text-primary);
  background: var(--bg-elev-2);
}

.nav-item.active {
  color: var(--text-primary);
  background: var(--bg-elev-2);
  font-weight: 500;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 16px;
  background: var(--accent);
  border-radius: 0 2px 2px 0;
}

.badge {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
}

/* 底部 */
.sidebar-foot {
  padding: 14px 16px 18px;
  border-top: 1px solid var(--line);
  display: grid;
  gap: 10px;
}

.cmd-trigger {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}

.cmd-trigger:hover {
  border-color: var(--accent-line);
  border-style: solid;
  color: var(--text-primary);
}

.cmd-trigger .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(125, 211, 252, 0.12);
}

.cmd-trigger kbd {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 10px;
}

.version {
  display: flex;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-faint);
  letter-spacing: 0.1em;
}

.version .ver {
  color: var(--text-muted);
}
</style>
