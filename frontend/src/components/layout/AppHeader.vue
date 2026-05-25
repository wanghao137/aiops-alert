<template>
  <header class="app-header">
    <div class="page-title">
      <h1>{{ title }}</h1>
      <span class="page-sub">{{ subtitle }}</span>
    </div>

    <div class="header-actions">
      <button class="search-pill" @click="openCommand">
        <Search :size="14" />
        <span>问点什么 · 例如：现在哪些对象在告警？</span>
        <span class="kbd">⌘ K</span>
      </button>

      <el-dropdown trigger="click" @command="onDemoCmd">
        <button class="demo-btn">
          <Sparkles :size="14" />
          演示数据
          <ChevronDown :size="14" />
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

      <div class="status-dot" :title="sseConnected ? 'SSE 已连接' : 'SSE 未连接'">
        <span class="dot" :class="{ ok: sseConnected }" />
        <span>{{ sseConnected ? '实时' : '离线' }}</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Sparkles,
  ChevronDown,
  PlayCircle,
  Trash2
} from 'lucide-vue-next'
import { cleanDemoData, seedDemoData } from '@/api/demo'
import { useCommandPaletteStore } from '@/composables/useCommandPalette'

defineProps<{ sseConnected?: boolean }>()

const route = useRoute()
const title = computed(() => (route.meta?.title as string) || '总览')
const subtitle = computed(() => '实时监控告警 · AI 增强')
const cmdStore = useCommandPaletteStore()

function openCommand() {
  cmdStore.open()
}

async function onDemoCmd(cmd: string) {
  if (cmd === 'seed') {
    const r = await seedDemoData()
    ElMessage.success(r || '演示数据已填充')
    setTimeout(() => window.location.reload(), 600)
  } else if (cmd === 'clean') {
    try {
      await ElMessageBox.confirm(
        '将删除所有对象、规则、渠道、事件、Incident。常用于演示前清场，确认继续？',
        '清空业务数据',
        { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
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
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  background: rgba(11, 17, 32, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
}

.page-title h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.page-sub {
  display: block;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.search-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 380px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-panel);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.search-pill:hover {
  border-color: var(--accent);
  color: var(--text-secondary);
}

.search-pill .kbd {
  margin-left: auto;
  padding: 2px 7px;
  border: 1px solid var(--line-subtle);
  border-radius: 5px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}

.demo-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-panel);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.demo-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.status-dot {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
}

.dot.ok {
  background: #10B981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.18);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.18); }
  50%      { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.05); }
}
</style>
