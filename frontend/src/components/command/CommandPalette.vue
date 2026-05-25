<template>
  <transition name="palette-fade">
    <div v-if="visible" class="palette-mask" @click="onMaskClick">
      <div class="palette" @click.stop>
        <header class="head">
          <Sparkles :size="16" class="head-icon" />
          <input
            ref="inputRef"
            v-model="prompt"
            class="head-input"
            placeholder="问点什么 · 例如：现在哪些对象在告警？打开规则页面..."
            @keydown.enter.prevent="onSubmit"
            @keydown.esc.prevent="close"
            @keydown.up.prevent="moveSelect(-1)"
            @keydown.down.prevent="moveSelect(1)"
          />
          <span class="kbd" v-if="!loading">Enter ↵</span>
          <span class="kbd loading" v-else>
            <span class="spinner" />
            思考中
          </span>
          <button class="close" @click="close"><X :size="14" /></button>
        </header>

        <div v-if="!result && !loading" class="suggestions">
          <div class="sug-title">试试这些</div>
          <button
            v-for="(s, i) in suggestions"
            :key="s.text"
            class="sug-item"
            :class="{ active: selected === i }"
            @click="useSuggestion(s)"
            @mouseenter="selected = i"
          >
            <component :is="s.icon" :size="13" />
            <span>{{ s.text }}</span>
            <small>{{ s.hint }}</small>
          </button>
        </div>

        <div v-if="result" class="result">
          <div class="result-head">
            <Bot :size="14" />
            <span>{{ result.answer }}</span>
            <small v-if="result.modelName">· {{ result.modelName }} · {{ result.durationMs }}ms</small>
          </div>

          <div v-if="result.intent === 'route' && result.routePath" class="route-card" @click="goto(result.routePath)">
            <ArrowRight :size="14" />
            打开 <code>{{ result.routePath }}</code>
          </div>

          <div v-if="result.intent === 'count_events'" class="count-grid">
            <div><span>累计匹配</span><strong>{{ result.total ?? 0 }}</strong></div>
            <div><span>当前待处理</span><strong>{{ result.pending ?? 0 }}</strong></div>
            <div><span>紧急</span><strong>{{ result.critical ?? 0 }}</strong></div>
          </div>

          <div v-if="result.events?.length" class="event-list">
            <div
              v-for="ev in result.events"
              :key="ev.id"
              class="event-item"
              @click="goEvent(ev.id)"
            >
              <span class="lv-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
              <div class="ev-meta">
                <div class="ev-row1">
                  <span class="ev-title">{{ ev.eventTitle }}</span>
                  <span class="lv-pill" :style="{
                    background: getAlertLevelMeta(ev.alertLevel).bg,
                    color: getAlertLevelMeta(ev.alertLevel).color
                  }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
                </div>
                <div class="ev-row2">
                  <span>{{ ev.objectName }}</span>
                  <span class="dot-sep" />
                  <span>{{ ev.metricName }}</span>
                  <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
                </div>
              </div>
              <ArrowRight :size="13" class="go-icon" />
            </div>
          </div>
        </div>

        <footer class="palette-foot">
          <span><kbd>↑↓</kbd> 选择</span>
          <span><kbd>Enter</kbd> 执行</span>
          <span><kbd>Esc</kbd> 关闭</span>
        </footer>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  Sparkles, X, ArrowRight, Bot,
  BellRing, Flame, LayoutDashboard, Settings, Send
} from 'lucide-vue-next'
import { runCommand, type CommandResult } from '@/api/command'
import { getAlertLevelMeta } from '@/utils/alertLevel'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const router = useRouter()
const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})
const prompt = ref('')
const loading = ref(false)
const result = ref<CommandResult>()
const selected = ref(0)
const inputRef = ref<HTMLInputElement>()

interface Suggestion {
  text: string
  hint: string
  icon: Component
}

const suggestions: Suggestion[] = [
  { text: '现在哪些对象在告警？', hint: '查询所有待处理事件', icon: BellRing },
  { text: '今天有多少紧急告警', hint: '统计 CRITICAL 级别事件', icon: Flame },
  { text: '打开总览大屏', hint: '跳转到 Dashboard', icon: LayoutDashboard },
  { text: '查看通知渠道', hint: '跳转到渠道页', icon: Send },
  { text: '系统设置', hint: '跳转到 LLM / 设置页', icon: Settings }
]

watch(visible, async (v) => {
  if (v) {
    prompt.value = ''
    result.value = undefined
    selected.value = 0
    await nextTick()
    inputRef.value?.focus()
  }
})

function close() {
  visible.value = false
}

function onMaskClick() {
  close()
}

function useSuggestion(s: Suggestion) {
  prompt.value = s.text
  onSubmit()
}

function moveSelect(delta: number) {
  if (result.value) return
  const n = suggestions.length
  selected.value = (selected.value + delta + n) % n
}

async function onSubmit() {
  // 没输入时执行选中的 suggestion
  const text = (prompt.value || suggestions[selected.value]?.text || '').trim()
  if (!text) return
  prompt.value = text
  loading.value = true
  result.value = undefined
  try {
    result.value = await runCommand(text)
    if (result.value.intent === 'route' && result.value.routePath) {
      // 路由意图直接跳，关闭面板
      goto(result.value.routePath)
    }
  } finally {
    loading.value = false
  }
}

function goto(path: string) {
  router.push(path)
  close()
}

function goEvent(id: number) {
  router.push({ path: '/events', query: { focus: id } })
  close()
}
</script>

<style scoped>
.palette-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: grid;
  place-items: start center;
  padding-top: 12vh;
}

.palette {
  width: min(720px, 92vw);
  max-height: 70vh;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--line-subtle);
  background: linear-gradient(180deg, #1F2937 0%, #111827 100%);
  box-shadow: 0 24px 80px -12px rgba(0, 0, 0, 0.6);
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
}

.head-icon {
  color: #C4B5FD;
  flex-shrink: 0;
}

.head-input {
  flex: 1;
  background: transparent;
  border: 0;
  outline: 0;
  color: var(--text-primary);
  font-size: 15px;
}

.head-input::placeholder {
  color: var(--text-muted);
}

.kbd {
  padding: 2px 8px;
  border: 1px solid var(--line-subtle);
  border-radius: 5px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}

.kbd.loading {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #C4B5FD;
}

.spinner {
  width: 9px;
  height: 9px;
  border: 1.5px solid #C4B5FD;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.close {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.close:hover {
  background: var(--bg-subtle);
  color: var(--text-primary);
}

.suggestions,
.result {
  overflow: auto;
  padding: 10px 12px;
}

.sug-title {
  padding: 4px 8px 8px;
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0.4px;
}

.sug-item {
  width: 100%;
  display: grid;
  grid-template-columns: 16px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.sug-item:hover,
.sug-item.active {
  background: var(--bg-subtle);
  border-color: var(--line);
  color: var(--text-primary);
}

.sug-item small {
  color: var(--text-muted);
  font-size: 11px;
}

.result-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(139,92,246,0.08);
  border: 1px solid rgba(139,92,246,0.25);
  color: var(--text-primary);
  font-size: 13px;
  margin-bottom: 10px;
}

.result-head small {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 400;
}

.route-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-subtle);
  color: var(--text-primary);
  cursor: pointer;
}

.route-card code {
  padding: 1px 7px;
  border-radius: 4px;
  background: var(--bg-panel);
  color: var(--accent);
  font-family: 'JetBrains Mono', monospace;
}

.count-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.count-grid > div {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
}

.count-grid span {
  color: var(--text-muted);
  font-size: 11px;
}

.count-grid strong {
  display: block;
  margin-top: 4px;
  color: var(--text-primary);
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}

.event-list { display: grid; gap: 6px; }

.event-item {
  display: grid;
  grid-template-columns: 4px 1fr 16px;
  gap: 10px;
  align-items: center;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
  cursor: pointer;
  transition: all 0.15s ease;
}

.event-item:hover {
  border-color: var(--accent);
}

.lv-bar {
  border-radius: 4px;
  height: 100%;
}

.ev-meta { display: grid; gap: 2px; min-width: 0; }

.ev-row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ev-title {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lv-pill {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.ev-row2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-size: 11px;
}

.ev-row2 code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-panel);
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.dot-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--text-muted);
  opacity: 0.5;
}

.go-icon { color: var(--text-muted); }

.palette-foot {
  display: flex;
  gap: 14px;
  padding: 8px 16px;
  border-top: 1px solid var(--line);
  background: var(--bg-base);
  color: var(--text-muted);
  font-size: 11px;
}

.palette-foot kbd {
  padding: 1px 6px;
  border: 1px solid var(--line-subtle);
  border-radius: 4px;
  background: var(--bg-subtle);
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  margin-right: 4px;
}

.palette-fade-enter-active,
.palette-fade-leave-active {
  transition: opacity 0.18s ease;
}
.palette-fade-enter-from,
.palette-fade-leave-to {
  opacity: 0;
}
.palette-fade-enter-active .palette,
.palette-fade-leave-active .palette {
  transition: transform 0.2s ease;
}
.palette-fade-enter-from .palette,
.palette-fade-leave-to .palette {
  transform: translateY(-12px);
}
</style>
