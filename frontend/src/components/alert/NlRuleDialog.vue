<template>
  <el-dialog
    v-model="visible"
    title=""
    width="720px"
    :close-on-click-modal="false"
    :show-close="!loading"
    class="nl-rule-dialog"
    @closed="onClosed"
  >
    <template #header>
      <div class="dlg-header">
        <div class="header-icon">
          <Sparkles :size="18" />
        </div>
        <div>
          <div class="header-title">AI 一句话建规则</div>
          <div class="header-sub">
            {{ availability === false
              ? '请先去「系统设置」配置 LLM 模型'
              : '描述你的告警需求，AI 会自动选择对象、指标、阈值、级别和渠道' }}
          </div>
        </div>
      </div>
    </template>

    <div v-if="!result" class="prompt-area">
      <el-input
        v-model="prompt"
        type="textarea"
        :rows="5"
        placeholder="例如：当生产 MySQL 主从延迟超过 5 分钟、连续触发 3 次，发企微到 DBA 群，紧急级别"
        :disabled="loading || availability === false"
        @keydown.ctrl.enter="onSubmit"
        @keydown.meta.enter="onSubmit"
      />

      <div class="example-row">
        <span class="example-label">试试这些</span>
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
        <Lightbulb :size="14" />
        当前没有可用的 LLM 模型。可以去
        <router-link to="/settings">系统设置</router-link>
        添加一个（OpenAI / DeepSeek / Qwen 兼容协议都行）。
      </div>
    </div>

    <div v-else class="result-area">
      <!-- 思考过程（推理类模型独有） -->
      <ThinkingPanel
        v-if="result.reasoning"
        :content="result.reasoning"
        :duration-ms="result.durationMs"
        title="AI 思考过程"
      />

      <!-- 概要 -->
      <div class="result-card">
        <div class="result-meta">
          <span class="meta-chip">
            <BadgeCheck :size="12" />
            {{ result.modelName || 'AI 模型' }}
          </span>
          <span class="meta-chip">耗时 {{ result.durationMs ?? '-' }}ms</span>
        </div>
        <div v-if="result.understanding" class="understanding">
          <Sparkles :size="13" />
          <span>{{ result.understanding }}</span>
        </div>

        <!-- 关键字段一览 -->
        <div class="summary-grid">
          <div>
            <span class="lbl">规则名称</span>
            <span class="val">{{ result.draft.ruleName || '-' }}</span>
          </div>
          <div>
            <span class="lbl">对象类型</span>
            <span class="val">
              <component :is="objectTypeMeta.icon" :size="13" :style="{ color: objectTypeMeta.color }" />
              {{ result.draft.objectTypeName || objectTypeMeta.label }}
            </span>
          </div>
          <div>
            <span class="lbl">告警级别</span>
            <span class="val">
              <component :is="levelMeta.icon" :size="13" :style="{ color: levelMeta.color }" />
              {{ levelMeta.label }}
            </span>
          </div>
          <div>
            <span class="lbl">触发策略</span>
            <span class="val">连续 {{ result.draft.triggerTimes }} 次 / 窗口 {{ result.draft.timeWindowMinutes }}min</span>
          </div>
        </div>

        <div class="formula-line">
          <span class="lbl">触发公式</span>
          <code>{{ formula }}</code>
        </div>

        <div class="rel-line" v-if="result.draft.objectIds?.length">
          <span class="lbl">对象</span>
          <span class="brief-chip" v-for="id in result.draft.objectIds" :key="id">
            {{ objectName(id) }}
          </span>
        </div>

        <div class="rel-line" v-if="result.draft.channelBindings?.length">
          <span class="lbl">渠道</span>
          <span class="brief-chip" v-for="b in result.draft.channelBindings" :key="b.channelId">
            {{ channelName(b.channelId) }}
            <small v-if="b.receiverValue">→ {{ b.receiverValue }}</small>
          </span>
        </div>
      </div>

      <!-- 警告 -->
      <div v-if="result.warnings?.length" class="warn-card">
        <div class="warn-title">
          <AlertTriangle :size="14" />
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
          <Sparkles :size="14" />&nbsp;{{ loading ? 'AI 思考中...' : '生成规则' }}
        </el-button>
      </template>
      <template v-else>
        <el-button @click="onRegenerate" :disabled="loading">重新生成</el-button>
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" @click="onApply">
          <Wand2 :size="14" />&nbsp;采用并填入表单
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
  // 拉对象/渠道用于回显
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
  // 重置状态
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
:deep(.nl-rule-dialog .el-dialog__header) {
  padding-bottom: 0;
}

.dlg-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.header-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #3B82F6, #8B5CF6);
  color: white;
  flex-shrink: 0;
}

.header-title {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
}

.header-sub {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 2px;
}

.prompt-area { display: grid; gap: 12px; }

.example-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.example-label {
  color: var(--text-muted);
  font-size: 12px;
  margin-right: 4px;
}

.example-chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.example-chip:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(59, 130, 246, 0.08);
}

.example-chip:disabled { opacity: 0.5; cursor: not-allowed; }

.empty-llm {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.3);
  color: #FCD34D;
  font-size: 12px;
}

.empty-llm a { color: var(--accent); margin: 0 4px; }

.result-area { display: grid; gap: 12px; }

.result-card {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-subtle);
  display: grid;
  gap: 10px;
}

.result-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-panel);
  color: var(--text-muted);
  font-size: 11px;
}

.understanding {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  border-left: 3px solid #8B5CF6;
  background: rgba(139, 92, 246, 0.08);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.summary-grid > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-panel);
  font-size: 12px;
}

.lbl { color: var(--text-muted); }

.val {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-primary);
  font-weight: 500;
}

.formula-line {
  display: grid;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-panel);
}

.formula-line code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--text-primary);
}

.rel-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.brief-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-panel);
  color: var(--text-secondary);
  font-size: 12px;
}

.brief-chip small {
  color: var(--text-muted);
  margin-left: 2px;
}

.warn-card {
  padding: 12px 14px;
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 10px;
  background: rgba(245, 158, 11, 0.08);
}

.warn-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #FCD34D;
  font-size: 13px;
  font-weight: 600;
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
