<template>
  <div class="app-shell">
    <AppSidebar @open-command="cmdStore.open" />
    <main class="app-main">
      <AppHeader :sse-connected="sseConnected" @open-command="cmdStore.open" />
      <div class="app-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <CommandPalette v-model="cmdStore.visible" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import AppHeader from './components/layout/AppHeader.vue'
import CommandPalette from './components/command/CommandPalette.vue'
import { useSse } from './composables/useSse'
import { useRealtimeStore } from './stores/realtime'
import { useCommandPaletteStore } from './composables/useCommandPalette'
import type { AlertEventItem } from './api/alertEvent'

const realtime = useRealtimeStore()
const cmdStore = useCommandPaletteStore()
const sseConnected = ref(false)

const { connected } = useSse((event, data) => {
  if (event === 'connected' || event === 'ping') {
    sseConnected.value = true
    return
  }
  if (event === 'event-created') {
    realtime.pushEvent(data as AlertEventItem)
    return
  }
  if (event === 'event-updated') {
    realtime.updateEventStatus(data as AlertEventItem)
    return
  }
  if (event === 'ai-summary') {
    const d = data as { eventId: number; status: string; summary?: string }
    realtime.applySummary(d.eventId, d.summary)
    return
  }
})

watch(connected, (v) => { sseConnected.value = v })

// Cmd+K / Ctrl+K 全局快捷键
function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    cmdStore.toggle()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 224px 1fr;
  min-height: 100vh;
  background: var(--bg-base);
}

.app-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-content {
  flex: 1;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>

<style>
.aiops-alert-toast {
  background: var(--bg-elev-1) !important;
  border: 1px solid var(--line) !important;
}
</style>
