<template>
  <el-dialog
    v-model="visible"
    title=""
    width="760px"
    :close-on-click-modal="false"
    :show-close="!loading"
    class="nl-rule-dialog"
    @closed="onClosed"
  >
    <template #header>
      <div class="dlg-header">
        <div class="header-eyebrow">
          <Sparkles :size="11" :stroke-width="1.8" />
          <span>AI BUILDER · ONE-SHOT</span>
        </div>
        <div class="header-title">用一句话描述你的告警需求</div>
        <div class="header-sub">
          {{ availability === false
            ? '当前没有可用的 LLM。请先去「系统设置」配置一个模型。'
            : 'AI 会替你选好对象、指标、阈值、级别和通知渠道，最终交给你确认。' }}
        </div>
      </div>
    </template>

    <!-- ========== Prompt 阶段 ========== -->
    <div v-if="!result" class="prompt-area">
      <div class="terminal-shell">
        <div class="term-head">
          <span class="term-dot r" />
          <span class="term-dot y" />
          <span class="term-dot g" />
          <span class="term-name">aiops:rule-builder</span>
          <span class="term-status" :class="{ ready: availability !== false }">
            <span class="dot-anim" />
            {{ availability === false ? 'OFFLINE' : 'READY' }}
          </span>
        </div>
        <div class="term-body">
          <div class="term-prompt">
            <span class="prompt-mark">$</span>
            <span class="prompt-cmd">describe</span>
            <span class="prompt-quote">"</span>
          </div>
          <textarea
            ref="textareaRef"
            v-model="prompt"
            class="term-input"
            :placeholder="placeholder"
            rows="4"
            :disabled="loading || availability === false"
            spellcheck="false"
            @keydown.ctrl.enter="onSubmit"
            @keydown.meta.enter="onSubmit"
          />
          <div class="term-foot">
            <span class="quote-end">"</span>
            <span class="kbd-hint">⌘ + Enter 发送</span>
          </div>
        </div>
      </div>

      <div class="example-row">
        <span class="example-label">EXAMPLES</span>
        <button
          v-for="ex in examples"
          :key="ex"
          class="example-chip"
          type="button"
          :disabled="loading || availability === false"
          @click="prompt = ex"
        >
          {{ ex }}
        </button>
      </div>

      <div v-if="availability === false" class="empty-llm">
        <Lightbulb :size="13" :stroke-width="1.8" />
        当前没有可用的 LLM 模型。可以去
        <router-link to="/settings">系统设置</router-link>
        添加一个（OpenAI / DeepSeek / Qwen 兼容协议都行）。
      </div>
    </div>

    <!-- ========== Result 阶段 ========== -->
    <div v-else class="result-area">
      <ThinkingPanel
        v-if="result.reasoning"
        :content="result.reasoning"
        :duration-ms="result.durationMs"
        title="AI 思考过程"
      />

      <div class="result-card">
        <div class="result-meta">
          <span class="meta-chip">
            <BadgeCheck :size="11" :stroke-width="1.8" />
            {{ result.modelName || 'AI 模型' }}
          </span>
          <span class="meta-chip">
            耗时 <b class="tabular-nums">{{ result.durationMs ?? '-' }}</b> ms
          </span>
        </div>

        <div v-if="result.understanding" class="understanding">
          <div class="under-mark">
            <Sparkles :size="12" :stroke-width="1.8" />
            <span>UNDERSTAND</span>
          </div>
          <div class="under-text">{{ result.understanding }}</div>
        </div>

        <div class="summary-grid">
          <div class="summary-cell">
            <span class="lbl">规则名称</span>
            <span class="val">{{ result.draft.ruleName || '-' }}</span>
          </div>
          <div class="summary-cell">
            <span class="lbl">对象类型</span>
            <span class="val">
              <component :is="objectTypeMeta.icon" :size="13" :stroke-width="1.8"
                :style="{ color: objectTypeMeta.color }" />
              {{ result.draft.objectTypeName || objectTypeMeta.label }}
            </span>
          </div>
          <div class="summary-cell">
            <span class="lbl">告警级别</span>
            <span class="val">
              <component :is="levelMeta.icon" :size="13" :stroke-width="1.8"
                :style="{ color: levelMeta.color }" />
              {{ levelMeta.label }}
            </span>
          </div>
          <div class="summary-cell">
            <span class="lbl">触发策略</span>
            <span class="val tabular-nums">连续 {{ result.draft.triggerTimes }} 次 / 窗口 {{ result.draft.timeWindowMinutes }}min</span>
          </div>
        </div>

        <div class="formula-line">
          <span class="lbl">触发公式</span>
          <div class="formula-block">
            <span class="formula-mark">▸</span>
            <code>{{ formula }}</code>
          </div>
        </div>

        <div class="rel-line" v-if="result.draft.objectIds?.length">
          <span class="lbl">监控对象</span>
          <div class="rel-chips">
            <span class="brief-chip" v-for="id in result.draft.objectIds" :key="id">
              {{ objectName(id) }}
            </span>
          </div>
        </div>

        <div class="rel-line" v-if="result.draft.channelBindings?.length">
          <span class="lbl">通知渠道</span>
          <div class="rel-chips">
            <span class="brief-chip" v-for="b in result.draft.channelBindings" :key="b.channelId">
              {{ channelName(b.channelId) }}
              <small v-if="b.receiverValue">→ {{ b.receiverValue }}</small>
            </span>
          </div>
        </div>
      </div>

      <div v-if="result.warnings?.length" class="warn-card">
        <div class="warn-title">
          <AlertTriangle :size="13" :stroke-width="1.8" />
          AI 给出的提醒（不会阻断保存，请人工确认）
        </div>
        <ul class="warn-list">
          <li v-for="(w, i) in result.warnings" :key="i">{{ w }}</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <template v-if="!result">
        <el-button @click="onCancel" :disabled="loading">取消</el-button>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!prompt.trim() || availability === false"
          @click="onSubmit"
        >
          <Sparkles :size="13" :stroke-width="1.8" />&nbsp;{{ loading ? 'AI 思考中…' : '生成规则' }}
        </el-button>
      </template>
      <template v-else>
        <el-button @click="onRegenerate" :disabled="loading">重新生成</el-button>
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" @click="onApply">
          <Wand2 :size="13" :stroke-width="1.8" />&nbsp;采用并填入表单
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Sparkles, BadgeCheck, AlertTriangle, Wand2, Lightbulb
} from 'lucide-vue-next'
import { checkNlRuleAvailability, draftNlRule, type NlRuleDraftResponse } from '@/api/nlRule'
import type { AlertRuleItem } from '@/api/alertRule'
import { listMonitorObjects, type MonitorObjectItem } from '@/api/monitorObject'
import { listAlertChannels, type AlertChannelItem } from '@/api/alertChannel'
import { getObjectTypeMeta } from '@/utils/objectType'
import { getAlertLevelMeta } from '@/utils/alertLevel'
import { useCatalogStore } from '@/stores/catalog'
import ThinkingPanel from '@/components/ai/ThinkingPanel.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'apply', draft: AlertRuleItem): void
}>()

const catalog = useCatalogStore()
const visible = ref(false)
const prompt = ref('')
const loading = ref(false)
const availability = ref<boolean>()
const result = ref<NlRuleDraftResponse>()
const objects = ref<MonitorObjectItem[]>([])
const channels = ref<AlertChannelItem[]>([])
const textareaRef = ref<HTMLTextAreaElement>()

const placeholder = '当生产 MySQL 主从延迟超过 5 分钟、连续触发 3 次，发企微到 DBA 群，紧急级别'

const examples = [
  '当生产 MySQL 主从延迟超过 5 分钟、连续触发 3 次，发企微到 DBA 群，紧急级别',
  '应用服务器 CPU 持续 10 分钟超过 85%，发邮件给运维',
  '客户信息同步作业失败 2 次以上，紧急通知数据团队',
  '数据加工作业输出条数为 0 时立即告警，发邮件 + 企微'
]

watch(() => props.modelValue, async (v) => {
  visible.value = v
  if (v) {
    await initAll()
    await nextTick()
    textareaRef.value?.focus()
  }
})

watch(visible, (v) => {
  if (!v) emit('update:modelValue', false)
})

async function initAll() {
  await catalog.ensureLoaded()
  try {
    availability.value = await checkNlRuleAvailability()
  } catch {
    availability.value = false
  }
  const [objs, chs] = await Promise.all([
    listMonitorObjects({ status: 'ENABLED' }),
    listAlertChannels({ status: 'ENABLED' })
  ])
  objects.value = objs
  channels.value = chs
}

async function onSubmit() {
  const text = prompt.value.trim()
  if (!text) return
  loading.value = true
  try {
    result.value = await draftNlRule(text)
  } finally {
    loading.value = false
  }
}

function onRegenerate() {
  result.value = undefined
  nextTick(() => textareaRef.value?.focus())
}

function onCancel() {
  visible.value = false
}

function onApply() {
  if (!result.value) return
  emit('apply', result.value.draft)
  ElMessage.success('已填入规则编辑表单，请确认后保存')
  visible.value = false
}

function onClosed() {
  prompt.value = ''
  result.value = undefined
}

const objectTypeMeta = computed(() => getObjectTypeMeta(result.value?.draft.objectType))
const levelMeta = computed(() => getAlertLevelMeta(result.value?.draft.alertLevel))

const formula = computed(() => {
  const draft = result.value?.draft
  if (!draft?.conditions?.length) return '-'
  const parts = draft.conditions.map((c) => {
    const op = catalog.findCompareOp(c.compareOp)
    const sym = op?.symbol || c.compareOp
    if (op?.inputKind === 'state') {
      return `${c.metricName} ${sym}`
    }
    return `${c.metricName} ${sym} ${c.thresholdValue || ''}${c.thresholdUnit || ''}`.trim()
  })
  const sep = draft.conditionLogic === 'OR' ? ' OR ' : ' AND '
  return parts.join(sep)
})

function objectName(id: number) {
  return objects.value.find((o) => o.id === id)?.objectName || `#${id}`
}

function channelName(id: number) {
  return channels.value.find((c) => c.id === id)?.channelName || `#${id}`
}
</script>


<style scoped>
:deep(.nl-rule-dialog .el-dialog) {
  background: var(--bg-elev-1);
  border: 1px solid var(--line);
  box-shadow: var(--inset);
}

:deep(.nl-rule-dialog .el-dialog__header) {
  padding-bottom: 0;
}

/* ========== Header ========== */
.dlg-header {
  display: grid;
  gap: 6px;
  padding-bottom: 4px;
}

.header-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: max-content;
  padding: 3px 9px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.18em;
}

.header-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.header-sub {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
}

/* ========== Terminal shell ========== */
.prompt-area { display: grid; gap: 14px; }

.terminal-shell {
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
  overflow: hidden;
  box-shadow: var(--inset);
}

.term-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
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
  color: var(--text-muted);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.term-status.ready { color: var(--ok); }

.dot-anim {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text-muted);
}

.term-status.ready .dot-anim {
  background: var(--ok);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.term-body {
  padding: 14px 16px 12px;
}

.term-prompt {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.prompt-mark { color: var(--accent); font-weight: 600; }
.prompt-cmd { color: var(--text-primary); }
.prompt-quote { color: var(--text-muted); }

.term-input {
  display: block;
  width: 100%;
  padding: 4px 0 4px 16px;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.7;
  resize: vertical;
  outline: none;
  caret-color: var(--accent);
}

.term-input::placeholder {
  color: var(--text-faint);
  font-style: italic;
}

.term-input:disabled {
  cursor: not-allowed;
  color: var(--text-faint);
}

.term-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
}

.quote-end { color: var(--text-muted); }

.kbd-hint {
  color: var(--text-faint);
  letter-spacing: 0.1em;
}

/* ========== Examples ========== */
.example-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.example-label {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  margin-right: 4px;
}

.example-chip {
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.example-chip:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.example-chip:disabled { opacity: 0.5; cursor: not-allowed; }

/* ========== Empty LLM ========== */
.empty-llm {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--warn-soft);
  border: 1px solid rgba(251, 191, 36, 0.3);
  color: var(--warn);
  font-size: 12px;
}

.empty-llm a { color: var(--accent); margin: 0 4px; }

/* ========== Result ========== */
.result-area {
  display: grid;
  gap: 12px;
  animation: fade-up 0.3s ease both;
}

.result-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
  display: grid;
  gap: 12px;
}

.result-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--bg-elev-1);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

.meta-chip b { color: var(--text-primary); font-weight: 500; }

.understanding {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--accent-line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
}

.under-mark {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: max-content;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--accent);
}

.under-text {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.summary-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
  font-size: 12px;
}

.lbl {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  white-space: nowrap;
}

.val {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-primary);
  font-weight: 500;
}

/* Formula */
.formula-line {
  display: grid;
  gap: 6px;
}

.formula-block {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
}

.formula-mark {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 12px;
  flex-shrink: 0;
}

.formula-block code {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-primary);
  word-break: break-all;
  line-height: 1.5;
}

/* Rel chips */
.rel-line {
  display: grid;
  gap: 6px;
}

.rel-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.brief-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-size: 12px;
}

.brief-chip small {
  color: var(--text-muted);
  margin-left: 2px;
  font-family: var(--font-mono);
}

/* Warn */
.warn-card {
  padding: 12px 14px;
  border: 1px solid rgba(251, 191, 36, 0.35);
  border-left: 2px solid var(--warn);
  border-radius: var(--radius-md);
  background: var(--warn-soft);
}

.warn-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--warn);
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
}

.warn-list {
  margin: 0;
  padding-left: 20px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
</style>
