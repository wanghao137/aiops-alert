<template>
  <section v-if="active" class="live-thinking-stream" :class="{ compact }">
    <header class="stream-head">
      <span class="stream-brand">
        <Brain :size="12" :stroke-width="1.8" />
        实时思考流
      </span>
      <span class="stream-state">
        <span class="pulse-dot" />
        {{ stateLabel }}
      </span>
    </header>

    <div ref="bodyRef" class="stream-body">
      <div
        v-for="line in lines"
        :key="line.id"
        class="stream-line"
        :class="line.kind"
      >
        <span class="line-time tabular-nums">{{ line.time }}</span>
        <span class="line-mark">›</span>
        <span class="line-text">{{ line.text }}</span>
      </div>
      <div class="stream-cursor">
        <span class="line-time tabular-nums">{{ elapsed }}</span>
        <span class="line-mark">›</span>
        <span class="typing">{{ currentTyping }}</span>
        <span class="caret" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Brain } from 'lucide-vue-next'

type Scene =
  | 'command'
  | 'nl-rule'
  | 'object-config'
  | 'event-summary'
  | 'threshold'
  | 'daily-brief'
  | 'llm-test'

interface StreamLine {
  id: number
  text: string
  time: string
  kind: 'info' | 'focus' | 'output'
}

const props = withDefaults(defineProps<{
  active: boolean
  scene: Scene
  subject?: string
  compact?: boolean
}>(), {
  subject: '',
  compact: false
})

const bodyRef = ref<HTMLElement>()
const lines = ref<StreamLine[]>([])
const currentTyping = ref('')
const startedAt = ref(0)
const elapsedMs = ref(0)
let lineTimer: ReturnType<typeof setInterval> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let cursorTimer: ReturnType<typeof setInterval> | undefined
let idx = 0
let lineId = 0

const stateLabel = computed(() => {
  const map: Record<Scene, string> = {
    command: '命令理解中',
    'nl-rule': '规则生成中',
    'object-config': '对象配置中',
    'event-summary': '摘要生成中',
    threshold: '阈值分析中',
    'daily-brief': '简报生成中',
    'llm-test': '模型探测中'
  }
  return map[props.scene]
})

const elapsed = computed(() => {
  const s = Math.floor(elapsedMs.value / 1000)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})

const messages = computed(() => {
  const subject = props.subject?.trim()
  const suffix = subject ? `：${subject}` : ''
  const base: Record<Scene, string[]> = {
    command: [
      `解析命令意图${suffix}`,
      '识别是否需要跳转页面、统计告警或查询事件',
      '读取当前告警状态和关键数量',
      '组织可直接执行的回答',
      '校验返回结构并准备渲染结果'
    ],
    'nl-rule': [
      `理解自然语言告警需求${suffix}`,
      '匹配监控对象类型、指标目录和比较符',
      '推断阈值、触发次数、时间窗口与告警级别',
      '选择可用通知渠道并检查缺失项',
      '生成规则草稿，等待人工确认保存'
    ],
    'object-config': [
      `理解监控对象配置意图${suffix}`,
      '识别对象类型、环境、负责人和关键标签',
      '抽取 IP、端口、调度周期、来源目标等扩展配置',
      '检查对象编码和保存字段是否完整',
      '生成对象草稿，等待人工确认保存'
    ],
    'event-summary': [
      `读取告警事件上下文${suffix}`,
      '比对指标当前值、阈值、规则和对象标签',
      '推断可能原因与影响范围',
      '整理处置动作和人工确认建议',
      '等待摘要回写到事件详情'
    ],
    threshold: [
      `读取指标历史样本${suffix}`,
      '计算 P50、P95、P99、最大值和波动区间',
      '结合指标类型判断保守、标准、激进阈值',
      '生成每个推荐值的解释',
      '等待选择后回填阈值输入框'
    ],
    'daily-brief': [
      '汇总今日告警、故障组和通知结果',
      '识别高优先级事件与异常变化',
      '压缩成适合汇报的一段态势叙述',
      '挑选需要重点讲解的告警样本',
      '组装简报卡片和关键数字'
    ],
    'llm-test': [
      `检查模型配置${suffix}`,
      '连接 OpenAI 兼容接口并发送最小提示词',
      '等待模型返回 content 或 reasoning_content',
      '解析耗时、模型名和连通状态',
      '生成测试结论和排错提示'
    ]
  }
  return base[props.scene]
})

watch(() => props.active, (active) => {
  if (active) start()
  else stop()
}, { immediate: true })

watch(() => [props.scene, props.subject], () => {
  if (props.active) start()
})

function start() {
  stop()
  startedAt.value = Date.now()
  elapsedMs.value = 0
  idx = 0
  lineId = 0
  lines.value = []
  currentTyping.value = '正在建立分析上下文'
  appendNext()
  lineTimer = setInterval(appendNext, 760)
  clockTimer = setInterval(() => {
    elapsedMs.value = Date.now() - startedAt.value
  }, 250)
  cursorTimer = setInterval(() => {
    const all = messages.value
    currentTyping.value = all[idx % all.length]
  }, 380)
}

function stop() {
  if (lineTimer) clearInterval(lineTimer)
  if (clockTimer) clearInterval(clockTimer)
  if (cursorTimer) clearInterval(cursorTimer)
  lineTimer = undefined
  clockTimer = undefined
  cursorTimer = undefined
}

function appendNext() {
  const all = messages.value
  if (!all.length) return
  const text = all[idx % all.length]
  lines.value.push({
    id: ++lineId,
    text,
    time: elapsed.value,
    kind: idx % all.length === 0 ? 'focus' : idx % all.length === all.length - 1 ? 'output' : 'info'
  })
  if (lines.value.length > (props.compact ? 4 : 8)) {
    lines.value.shift()
  }
  idx += 1
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  })
}

onBeforeUnmount(stop)
</script>

<style scoped>
.live-thinking-stream {
  display: grid;
  gap: 8px;
  border: 1px solid var(--accent-line);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, var(--accent-soft), transparent 62%),
    var(--bg-elev-2);
  box-shadow: var(--inset);
  overflow: hidden;
}

.stream-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--accent-line);
}

.stream-brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.16em;
}

.stream-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  color: var(--ok);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.pulse-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-soft 2s ease-in-out infinite;
}

.stream-body {
  max-height: 184px;
  overflow: auto;
  padding: 9px 12px 12px;
  background:
    repeating-linear-gradient(
      to bottom,
      transparent 0,
      transparent 27px,
      color-mix(in srgb, var(--accent) 8%, transparent) 28px
    );
}

.compact .stream-body {
  max-height: 132px;
}

.stream-line,
.stream-cursor {
  display: grid;
  grid-template-columns: 42px 12px 1fr;
  gap: 7px;
  align-items: baseline;
  min-height: 24px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.55;
}

.line-time {
  color: var(--text-faint);
  font-size: 10px;
}

.line-mark {
  color: var(--accent);
  font-weight: 700;
}

.stream-line.focus .line-text {
  color: var(--text-primary);
}

.stream-line.output .line-text {
  color: var(--ok);
}

.stream-cursor {
  color: var(--accent);
}

.typing {
  color: var(--text-primary);
}

.caret {
  display: inline-block;
  width: 6px;
  height: 13px;
  margin-left: 4px;
  background: var(--accent);
  vertical-align: -2px;
  animation: blink 1s steps(2) infinite;
}

.stream-body::-webkit-scrollbar {
  width: 5px;
}

.stream-body::-webkit-scrollbar-thumb {
  background: var(--line-strong);
  border-radius: 3px;
}

@media (max-width: 640px) {
  .stream-head {
    display: grid;
  }
}
</style>
