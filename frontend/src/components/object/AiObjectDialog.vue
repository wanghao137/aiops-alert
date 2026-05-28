<template>
  <el-dialog
    v-model="visible"
    title=""
    width="760px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!loading"
    class="ai-object-dialog"
    @closed="onClosed"
  >
    <template #header>
      <div class="dlg-header">
        <div class="header-eyebrow">
          <Sparkles :size="11" :stroke-width="1.8" />
          <span>AI 配对象 · 一句话</span>
        </div>
        <div class="header-title">用一句话描述要监控的对象</div>
        <div class="header-sub">
          {{ availability === false
            ? '当前没有可用的 LLM。请先去「系统设置」配置一个模型。'
            : 'AI 会补齐对象类型、负责人、标签、描述和扩展配置，最终仍由你确认保存。' }}
        </div>
      </div>
    </template>

    <div v-if="!result" class="prompt-area">
      <div class="terminal-shell">
        <div class="term-head">
          <span class="term-dot r" />
          <span class="term-dot y" />
          <span class="term-dot g" />
          <span class="term-name">aiops:object-builder</span>
          <span class="term-status" :class="{ ready: availability !== false }">
            <span class="dot-anim" />
            {{ availability === false ? '离线 OFFLINE' : '就绪 READY' }}
          </span>
        </div>
        <div class="term-body">
          <div class="term-prompt">
            <span class="prompt-mark">$</span>
            <span class="prompt-cmd">describe-object</span>
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
        <span class="example-label">示例 EXAMPLES</span>
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

      <LiveThinkingStream
        v-if="loading"
        :active="loading"
        scene="object-config"
        :subject="prompt"
      />
    </div>

    <div v-else class="result-area">
      <ThinkingPanel
        v-if="result.reasoning"
        :content="result.reasoning"
        :duration-ms="result.durationMs"
        title="AI 思考过程"
      />

      <div class="object-result-card">
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
            <span>配置理解</span>
          </div>
          <div class="under-text">{{ result.understanding }}</div>
        </div>

        <div class="summary-grid">
          <div class="summary-cell span-2">
            <span class="lbl">对象名称</span>
            <span class="val strong">{{ result.draft.objectName || '-' }}</span>
          </div>
          <div class="summary-cell">
            <span class="lbl">对象类型</span>
            <span class="val">
              <component :is="objectTypeMeta.icon" :size="13" :stroke-width="1.8" :style="{ color: objectTypeMeta.color }" />
              {{ objectTypeMeta.label }} / {{ result.draft.objectType }}
            </span>
          </div>
          <div class="summary-cell">
            <span class="lbl">对象编码</span>
            <span class="val tabular-nums">{{ result.draft.objectCode || '保存时自动生成' }}</span>
          </div>
          <div class="summary-cell">
            <span class="lbl">负责人</span>
            <span class="val">{{ result.draft.ownerName || '-' }}</span>
          </div>
          <div class="summary-cell">
            <span class="lbl">状态</span>
            <span class="val">{{ result.draft.status === 'DISABLED' ? '停用' : '启用' }}</span>
          </div>
        </div>

        <div v-if="result.draft.tags" class="rel-line">
          <span class="lbl">标签</span>
          <div class="rel-chips">
            <span v-for="tag in tagList(result.draft.tags)" :key="tag" class="brief-chip">{{ tag }}</span>
          </div>
        </div>

        <div v-if="result.draft.description" class="description-line">
          <span class="lbl">描述</span>
          <p>{{ result.draft.description }}</p>
        </div>

        <div v-if="result.draft.extConfig" class="config-line">
          <span class="lbl">扩展配置 JSON</span>
          <pre>{{ result.draft.extConfig }}</pre>
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
          <Sparkles :size="13" :stroke-width="1.8" />&nbsp;{{ loading ? 'AI 思考中…' : '生成对象配置' }}
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
import { Sparkles, BadgeCheck, AlertTriangle, Wand2, Lightbulb } from 'lucide-vue-next'
import {
  checkMonitorObjectAiAvailability,
  draftMonitorObject,
  type MonitorObjectDraftResponse,
  type MonitorObjectItem
} from '@/api/monitorObject'
import { getObjectTypeMeta } from '@/utils/objectType'
import LiveThinkingStream from '@/components/ai/LiveThinkingStream.vue'
import ThinkingPanel from '@/components/ai/ThinkingPanel.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'apply', draft: MonitorObjectItem): void
}>()

const visible = ref(false)
const prompt = ref('')
const loading = ref(false)
const availability = ref<boolean>()
const result = ref<MonitorObjectDraftResponse>()
const textareaRef = ref<HTMLTextAreaElement>()

const placeholder = '添加生产 MySQL 主库，IP 10.0.0.21，端口 3306，DBA 值班负责，核心 7x24'
const examples = [
  '添加生产 MySQL 主库，IP 10.0.0.21，端口 3306，DBA 值班负责，核心 7x24',
  '新增一台生产 Web 服务器 10.0.1.15，Nginx，SRE 团队负责',
  '客户信息同步作业，每 5 分钟从 CRM 同步到数仓，数据团队负责',
  '每日凌晨 2 点订单汇总加工作业，关注空跑、失败和超时'
]

watch(() => props.modelValue, async (v) => {
  visible.value = v
  if (v) {
    await init()
    await nextTick()
    textareaRef.value?.focus()
  }
})

watch(visible, (v) => {
  if (!v) emit('update:modelValue', false)
})

const objectTypeMeta = computed(() => getObjectTypeMeta(result.value?.draft.objectType))

async function init() {
  try {
    availability.value = await checkMonitorObjectAiAvailability()
  } catch {
    availability.value = false
  }
}

async function onSubmit() {
  const text = prompt.value.trim()
  if (!text) return
  loading.value = true
  try {
    result.value = await draftMonitorObject(text)
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
  ElMessage.success('已填入对象编辑表单，请确认后保存')
  visible.value = false
}

function onClosed() {
  prompt.value = ''
  result.value = undefined
}

function tagList(tags?: string) {
  if (!tags) return []
  return tags.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
}
</script>

<style scoped>
:deep(.ai-object-dialog.el-dialog) {
  background: var(--bg-elev-1);
  border: 1px solid var(--line);
  box-shadow: var(--inset);
}

:deep(.ai-object-dialog.el-dialog .el-dialog__header) {
  padding-bottom: 0;
}

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

.prompt-area,
.result-area {
  display: grid;
  gap: 14px;
}

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
  background: currentColor;
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.term-body {
  padding: 14px 16px 12px;
}

.term-prompt {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 12px;
}

.prompt-mark { color: var(--accent); font-weight: 600; }
.prompt-cmd { color: var(--text-primary); }
.prompt-quote,
.quote-end { color: var(--text-muted); }

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

.term-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
}

.example-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.example-label,
.lbl {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  white-space: nowrap;
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

.example-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-llm {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid rgba(251, 191, 36, 0.3);
  border-radius: var(--radius-sm);
  background: var(--warn-soft);
  color: var(--warn);
  font-size: 12px;
}

.empty-llm a {
  margin: 0 4px;
  color: var(--accent);
}

.object-result-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
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
  border: 1px solid var(--line);
  border-radius: 999px;
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
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
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
  min-width: 0;
}

.span-2 { grid-column: span 2; }

.val {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-primary);
  font-weight: 500;
  min-width: 0;
  text-align: right;
}

.val.strong {
  font-size: 13px;
}

.rel-line,
.description-line,
.config-line {
  display: grid;
  gap: 6px;
}

.rel-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.brief-chip {
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-size: 12px;
}

.description-line p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.config-line pre {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

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
  margin-bottom: 6px;
  color: var(--warn);
  font-size: 13px;
  font-weight: 500;
}

.warn-list {
  margin: 0;
  padding-left: 20px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: span 1;
  }
}
</style>
