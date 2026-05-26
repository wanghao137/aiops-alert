<template>
  <header class="app-header">
    <div class="page-block">
      <div class="eyebrow-line">
        <span class="eyebrow">{{ eyebrow }}</span>
        <span class="trail" />
      </div>
      <h1 class="page-title">{{ title }}</h1>
    </div>

    <div class="actions">
      <button class="search-pill" @click="openCommand">
        <Search :size="13" :stroke-width="1.6" />
        <span>问点什么 — 现在哪些对象在告警？</span>
        <kbd>⌘ K</kbd>
      </button>

      <ThemeSwitcher />

      <el-dropdown trigger="click" @command="onDemoCmd">
        <button class="demo-btn">
          <Sparkles :size="13" :stroke-width="1.6" />
          演示数据
          <ChevronDown :size="12" :stroke-width="1.6" />
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="seed">
              <PlayCircle :size="14" /> &nbsp;一键填充演示数据
            </el-dropdown-item>
            <el-dropdown-item command="clean" divided>
              <Trash2 :size="14" /> &nbsp;清空全部业务数据
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div class="conn">
        <span class="conn-dot" :class="{ live: sseConnected }" />
        <span class="conn-text">{{ sseConnected ? 'LIVE' : 'OFFLINE' }}</span>
        <span class="conn-time">{{ now }}</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Sparkles, ChevronDown, PlayCircle, Trash2 } from 'lucide-vue-next'
import { cleanDemoData, seedDemoData } from '@/api/demo'
import { useCommandPaletteStore } from '@/composables/useCommandPalette'
import ThemeSwitcher from './ThemeSwitcher.vue'

defineProps<{ sseConnected?: boolean }>()

const route = useRoute()
const cmdStore = useCommandPaletteStore()

const eyebrow = computed(() => (route.meta?.eyebrow as string) || (route.meta?.title as string) || 'OVERVIEW')
const title = computed(() => (route.meta?.title as string) || '总览')

const now = ref(formatNow())
let timer: number | undefined
function formatNow() {
  const d = new Date()
  const hh = d.getHours().toString().padStart(2, '0')
  const mm = d.getMinutes().toString().padStart(2, '0')
  const ss = d.getSeconds().toString().padStart(2, '0')
  return `${hh}:${mm}:${ss}`
}
onMounted(() => {
  timer = window.setInterval(() => { now.value = formatNow() }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

function openCommand() { cmdStore.open() }

async function onDemoCmd(cmd: string) {
  if (cmd === 'seed') {
    const r = await seedDemoData()
    ElMessage.success(r || '演示数据已填充')
    setTimeout(() => window.location.reload(), 600)
  } else if (cmd === 'clean') {
    try {
      await ElMessageBox.confirm(
        '将删除所有对象、规则、渠道、事件、Incident。确认继续？',
        '清空业务数据',
        { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
    const r = await cleanDemoData()
    ElMessage.success(r || '已清空')
    setTimeout(() => window.location.reload(), 600)
  }
}
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 28px 18px;
  background: var(--bg-base);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--line);
}

.eyebrow-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.eyebrow {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.trail {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--line-strong), transparent);
  max-width: 80px;
}

.page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 380px;
  padding: 9px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-muted);
  cursor: pointer;
  font-family: var(--font-sans);
  font-size: 12px;
  transition: all 0.15s ease;
}

.search-pill:hover {
  border-color: var(--accent-line);
  color: var(--text-secondary);
}

.search-pill kbd {
  margin-left: auto;
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 10px;
}

.demo-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.demo-btn:hover {
  border-color: var(--accent-line);
  color: var(--accent);
}

.conn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-muted);
}

.conn-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-faint);
}

.conn-dot.live {
  background: var(--ok);
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.18);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.conn-text {
  color: var(--text-secondary);
}

.conn-time {
  color: var(--text-muted);
  margin-left: 4px;
}
</style>
