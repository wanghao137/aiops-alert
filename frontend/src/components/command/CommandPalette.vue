<template>
  <transition name="palette-fade">
    <div v-if="visible" class="palette-mask" @click="onMaskClick">
      <div class="palette" @click.stop>
        <!-- ========== Terminal head ========== -->
        <header class="term-head">
          <span class="term-dot r" />
          <span class="term-dot y" />
          <span class="term-dot g" />
          <span class="term-name">aiops:command</span>
          <span class="term-status">
            <span class="dot-anim" />
            {{ loading ? 'THINKING' : 'READY' }}
          </span>
          <button class="close" @click="close"><X :size="13" :stroke-width="1.8" /></button>
        </header>

        <!-- ========== Input row ========== -->
        <div class="input-row">
          <span class="prompt-mark">›</span>
          <input
            ref="inputRef"
            v-model="prompt"
            class="head-input"
            placeholder="问点什么 · 例如：现在哪些对象在告警？打开规则页面..."
            spellcheck="false"
            autocomplete="off"
            @keydown.enter.prevent="onSubmit"
            @keydown.esc.prevent="close"
            @keydown.up.prevent="moveSelect(-1)"
            @keydown.down.prevent="moveSelect(1)"
          />
          <span class="caret" :class="{ hide: prompt.length > 0 }" />
          <span v-if="!loading" class="kbd">↵</span>
          <span v-else class="kbd loading">
            <span class="spinner" />
            思考中
          </span>
        </div>

        <!-- ========== Suggestions ========== -->
        <div v-if="!result && !loading" class="body suggestions">
          <div class="eyebrow">QUICK COMMANDS</div>
          <button
            v-for="(s, i) in suggestions"
            :key="s.text"
            class="sug-item"
            :class="{ active: selected === i }"
            @click="useSuggestion(s)"
            @mouseenter="selected = i"
          >
            <span class="sug-icon">
              <component :is="s.icon" :size="13" :stroke-width="1.8" />
            </span>
            <span class="sug-text">{{ s.text }}</span>
            <small class="sug-hint">{{ s.hint }}</small>
            <span class="sug-arrow">↵</span>
          </button>
        </div>

        <!-- ========== Result ========== -->
        <div v-if="result" class="body result">
          <div class="result-head">
            <div class="ans-mark">
              <Bot :size="13" :stroke-width="1.8" />
              <span>ANSWER</span>
            </div>
            <div class="ans-text">{{ result.answer }}</div>
            <div class="ans-meta">
              <span v-if="result.modelName">{{ result.modelName }}</span>
              <span v-if="result.durationMs"> · <b class="tabular-nums">{{ result.durationMs }}</b>ms</span>
            </div>
          </div>

          <ThinkingPanel
            v-if="result.reasoning"
            :content="result.reasoning"
            title="AI 思考过程"
          />

          <div v-if="result.intent === 'route' && result.routePath" class="route-card"
            @click="goto(result.routePath)">
            <ArrowRight :size="13" :stroke-width="1.8" />
            <span>打开</span>
            <code>{{ result.routePath }}</code>
          </div>

          <div v-if="result.intent === 'count_events'" class="count-grid">
            <div class="count-cell">
              <span class="lbl">累计匹配</span>
              <strong class="val tabular-nums">{{ result.total ?? 0 }}</strong>
            </div>
            <div class="count-cell">
              <span class="lbl">当前待处理</span>
              <strong class="val tabular-nums">{{ result.pending ?? 0 }}</strong>
            </div>
            <div class="count-cell">
              <span class="lbl">紧急</span>
              <strong class="val tabular-nums" :class="{ urgent: (result.critical ?? 0) > 0 }">
                {{ result.critical ?? 0 }}
              </strong>
            </div>
          </div>

          <div v-if="result.events?.length" class="event-list">
            <div class="eyebrow inline">MATCHING EVENTS · {{ result.events.length }}</div>
            <button
              v-for="ev in result.events"
              :key="ev.id"
              class="event-item"
              type="button"
              @click="goEvent(ev.id)"
            >
              <span class="lv-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
              <div class="ev-meta">
                <div class="ev-row1">
                  <span class="ev-title">{{ ev.eventTitle }}</span>
                  <span class="lv-tag" :style="{
                    color: getAlertLevelMeta(ev.alertLevel).color,
                    borderColor: getAlertLevelMeta(ev.alertLevel).color
                  }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
                </div>
                <div class="ev-row2">
                  <span class="ev-obj">{{ ev.objectName }}</span>
                  <span class="sep">·</span>
                  <span>{{ ev.metricName }}</span>
                  <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
                </div>
              </div>
              <ArrowRight :size="13" :stroke-width="1.8" class="go-icon" />
            </button>
          </div>
        </div>

        <!-- ========== Footer ========== -->
        <footer class="palette-foot">
          <span><kbd>↑↓</kbd> 选择</span>
          <span><kbd>Enter</kbd> 执行</span>
          <span><kbd>Esc</kbd> 关闭</span>
          <span class="spacer" />
          <span class="brand">AIOPS · COMMAND</span>
        </footer>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  X, ArrowRight, Bot,
  BellRing, Flame, LayoutDashboard, Settings, Send
} from 'lucide-vue-next'
import { runCommand, type CommandResult } from '@/api/command'
import { getAlertLevelMeta } from '@/utils/alertLevel'
import ThinkingPanel from '@/components/ai/ThinkingPanel.vue'

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
  window.removeEventListener('keydown', onGlobalKeydown)
  if (v) {
    prompt.value = ''
    result.value = undefined
    selected.value = 0
    window.addEventListener('keydown', onGlobalKeydown)
    await nextTick()
    inputRef.value?.focus()
  }
})

function close() {
  visible.value = false
  window.removeEventListener('keydown', onGlobalKeydown)
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (!visible.value || e.key !== 'Escape') return
  e.preventDefault()
  close()
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
  const text = (prompt.value || suggestions[selected.value]?.text || '').trim()
  if (!text) return
  prompt.value = text
  loading.value = true
  result.value = undefined
  try {
    result.value = await runCommand(text)
    if (result.value.intent === 'route' && result.value.routePath) {
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

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<style scoped>
/* ========== Mask ========== */
.palette-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  display: grid;
  place-items: start center;
  padding-top: 12vh;
}

:root[data-theme='light'] .palette-mask {
  background: rgba(15, 17, 21, 0.32);
}

/* ========== Palette ========== */
.palette {
  width: min(720px, 92vw);
  max-height: 76vh;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-lg);
  background: var(--bg-elev-1);
  box-shadow:
    var(--inset),
    0 24px 64px -12px rgba(0, 0, 0, 0.55);
  display: grid;
  grid-template-rows: auto auto 1fr auto;
}

/* ========== Terminal head ========== */
.term-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  background: var(--bg-elev-3);
  border-bottom: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 11px;
}

.term-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.term-dot.r { background: var(--critical); }
.term-dot.y { background: var(--warn); }
.term-dot.g { background: var(--ok); }

.term-name {
  margin-left: 8px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.term-status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--ok);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.dot-anim {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ok);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.close {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.close:hover {
  background: var(--bg-elev-2);
  color: var(--text-primary);
}

/* ========== Input row ========== */
.input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-elev-1);
}

.prompt-mark {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 600;
  flex-shrink: 0;
}

.head-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: 0;
  outline: 0;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 14px;
  caret-color: var(--accent);
}

.head-input::placeholder {
  color: var(--text-faint);
  font-style: italic;
}

.caret {
  display: inline-block;
  width: 7px;
  height: 16px;
  background: var(--accent);
  animation: blink 1s steps(2) infinite;
  flex-shrink: 0;
}

.caret.hide { display: none; }

.kbd {
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.06em;
}

.kbd.loading {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--accent);
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.spinner {
  width: 9px;
  height: 9px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== Body ========== */
.body {
  overflow: auto;
  padding: 12px 14px;
  display: grid;
  gap: 10px;
}

.eyebrow.inline { margin: 0; }

/* ========== Suggestions ========== */
.suggestions { padding-bottom: 14px; }

.sug-item {
  display: grid;
  grid-template-columns: 22px 1fr auto auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: all 0.12s ease;
}

.sug-item:hover,
.sug-item.active {
  background: var(--bg-elev-2);
  border-color: var(--line);
  color: var(--text-primary);
}

.sug-item.active {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.sug-icon {
  display: grid;
  place-items: center;
  color: var(--text-muted);
}

.sug-item.active .sug-icon { color: var(--accent); }

.sug-text {
  font-size: 13px;
  font-weight: 500;
}

.sug-hint {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.sug-arrow {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--bg-elev-3);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.12s ease;
}

.sug-item.active .sug-arrow {
  opacity: 1;
  background: var(--accent);
  color: var(--bg-base);
}

/* ========== Result ========== */
.result-head {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--accent-line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
}

.ans-mark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: max-content;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--accent);
}

.ans-text {
  color: var(--text-primary);
  font-size: 13.5px;
  line-height: 1.6;
}

.ans-meta {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.ans-meta b { color: var(--text-secondary); font-weight: 500; }

/* Route card */
.route-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.route-card:hover {
  border-color: var(--accent);
  color: var(--text-primary);
}

.route-card code {
  padding: 1px 7px;
  border-radius: 3px;
  background: var(--bg-elev-3);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 12px;
}

/* Count grid */
.count-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.count-cell {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
}

.count-cell .lbl {
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.count-cell .val {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.count-cell .val.urgent { color: var(--critical); }

/* Event list */
.event-list { display: grid; gap: 6px; }

.event-item {
  display: grid;
  grid-template-columns: 3px 1fr 16px;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  cursor: pointer;
  text-align: left;
  font-family: var(--font-sans);
  transition: all 0.15s ease;
}

.event-item:hover {
  border-color: var(--accent);
  background: var(--bg-elev-1);
}

.lv-bar {
  align-self: stretch;
  border-radius: 3px;
}

.ev-meta { display: grid; gap: 4px; min-width: 0; }

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

.lv-tag {
  padding: 1px 7px;
  border: 1px solid;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.ev-row2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
}

.ev-obj { color: var(--text-secondary); }
.sep { color: var(--text-faint); }

.ev-row2 code {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-elev-3);
  color: var(--text-primary);
}

.go-icon { color: var(--text-muted); }

/* ========== Footer ========== */
.palette-foot {
  display: flex;
  gap: 14px;
  padding: 8px 16px;
  border-top: 1px solid var(--line);
  background: var(--bg-elev-2);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.palette-foot kbd {
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--bg-elev-1);
  font-family: var(--font-mono);
  font-size: 10px;
  margin-right: 4px;
  color: var(--text-secondary);
}

.spacer { flex: 1; }

.brand {
  color: var(--text-faint);
  letter-spacing: 0.18em;
}

/* ========== Transitions ========== */
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
  transition: transform 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}
.palette-fade-enter-from .palette,
.palette-fade-leave-to .palette {
  transform: translateY(-12px);
}
</style>
