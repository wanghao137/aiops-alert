<template>
  <div class="condition-editor">
    <div class="logic-row">
      <span class="logic-label">多条件关系</span>
      <el-radio-group v-model="logic" size="small" @change="emit('update:logic', logic)">
        <el-radio-button label="AND">全部满足 (AND)</el-radio-button>
        <el-radio-button label="OR">任一满足 (OR)</el-radio-button>
      </el-radio-group>
    </div>

    <div v-for="(item, idx) in modelValue" :key="idx" class="condition-row">
      <div class="row-index">{{ idx + 1 }}</div>

      <el-select
        v-model="item.metricCode"
        placeholder="选择指标"
        class="metric-select"
        :disabled="!objectType"
        @change="(val: string) => onMetricChange(idx, val)"
      >
        <el-option
          v-for="m in metrics"
          :key="m.code"
          :label="m.name"
          :value="m.code"
        >
          <span class="opt">
            <span>{{ m.name }}</span>
            <small>{{ m.unit ? m.unit : m.valueType === 'state' ? '状态' : '' }}</small>
          </span>
        </el-option>
      </el-select>

      <el-select v-model="item.compareOp" placeholder="比较" class="op-select">
        <el-option
          v-for="op in compareOpsFor(item.metricCode)"
          :key="op.code"
          :label="op.label"
          :value="op.code"
        >
          <span class="opt">
            <span>{{ op.label }}</span>
            <small>{{ op.symbol }}</small>
          </span>
        </el-option>
      </el-select>

      <!-- 数值阈值 -->
      <div v-if="metricKindOf(item.metricCode) === 'numeric'" class="threshold-wrap">
        <el-input v-model="item.thresholdValue" placeholder="阈值" class="threshold-input">
          <template v-if="metricUnitOf(item.metricCode)" #append>
            {{ metricUnitOf(item.metricCode) }}
          </template>
        </el-input>
        <el-popover
          v-if="objectType"
          :width="320"
          trigger="click"
          placement="bottom-end"
          @before-enter="onAiHelperOpen(idx)"
        >
          <template #reference>
            <button class="ai-helper-btn" type="button" :title="'AI 阈值推荐'">
              <Sparkles :size="13" />
            </button>
          </template>
          <div class="reco-pop">
            <div class="reco-head">
              <Sparkles :size="13" />
              <span>AI 阈值推荐</span>
              <small v-if="reco">{{ reco.source === 'HISTORY'
                ? `基于近 7 天 ${reco.samples} 个样本`
                : '基于经验值（暂无历史样本）' }}</small>
            </div>
            <LiveThinkingStream
              v-if="recoLoading"
              :active="recoLoading"
              scene="threshold"
              :subject="curMetricName(idx)"
              compact
            />
            <div v-if="reco?.recommendations?.length" class="reco-list">
              <button
                v-for="r in reco.recommendations"
                :key="r.label"
                class="reco-item"
                type="button"
                @click="applyReco(idx, r)"
              >
                <div class="reco-label">
                  <span>{{ r.label }}</span>
                  <strong>{{ r.value }}{{ reco?.unit || '' }}</strong>
                </div>
                <small>{{ r.explain }}</small>
              </button>
            </div>
            <div v-if="reco?.source === 'HISTORY'" class="reco-stats">
              <span>P50 <b>{{ reco.p50 }}</b></span>
              <span>P95 <b>{{ reco.p95 }}</b></span>
              <span>P99 <b>{{ reco.p99 }}</b></span>
              <span>Max <b>{{ reco.max }}</b></span>
            </div>
            <div v-if="!recoLoading && !reco" class="reco-empty">点击触发推荐</div>
          </div>
        </el-popover>
      </div>

      <!-- 状态枚举 -->
      <el-select
        v-else-if="metricKindOf(item.metricCode) === 'state' && stateOptionsOf(item.metricCode).length"
        v-model="item.thresholdValue"
        placeholder="目标状态"
        class="threshold-input"
      >
        <el-option
          v-for="o in stateOptionsOf(item.metricCode)"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>

      <!-- 占位 -->
      <div v-else class="threshold-input placeholder">—</div>

      <button class="del-btn" type="button" @click="onRemove(idx)" :disabled="modelValue.length <= 1">
        <X :size="14" />
      </button>
    </div>

    <button class="add-btn" type="button" @click="onAdd" :disabled="!objectType">
      <Plus :size="14" />&nbsp;添加条件
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Plus, X, Sparkles } from 'lucide-vue-next'
import type { AlertRuleConditionItem } from '@/api/alertRule'
import type { MetricItem } from '@/api/metricCatalog'
import { useCatalogStore } from '@/stores/catalog'
import { recommendThreshold, type ThresholdReco, type ThresholdRecoItem } from '@/api/threshold'
import LiveThinkingStream from '@/components/ai/LiveThinkingStream.vue'

const props = defineProps<{
  modelValue: AlertRuleConditionItem[]
  objectType: string
  /** 单选时的代表 objectId（多对象时不传，使用全局历史） */
  objectId?: number
  logic?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: AlertRuleConditionItem[]): void
  (e: 'update:logic', v: string): void
}>()

const catalog = useCatalogStore()
const logic = ref(props.logic || 'AND')
const recoLoading = ref(false)
const reco = ref<ThresholdReco>()

watch(() => props.logic, (v) => { logic.value = v || 'AND' })

const metrics = computed<MetricItem[]>(() => catalog.metricsOfType(props.objectType))

function metricKindOf(code?: string) {
  return catalog.findMetric(props.objectType, code)?.valueType || 'numeric'
}

function metricUnitOf(code?: string) {
  return catalog.findMetric(props.objectType, code)?.unit
}

function stateOptionsOf(code?: string) {
  return catalog.findMetric(props.objectType, code)?.options || []
}

function compareOpsFor(metricCode?: string) {
  const all = catalog.data?.compareOps || []
  const kind = metricKindOf(metricCode)
  if (kind === 'state') {
    return all.filter((op) => op.inputKind === 'state' || op.inputKind === 'any')
  }
  return all.filter((op) => op.inputKind === 'numeric' || op.inputKind === 'any')
}

function curMetricName(idx: number) {
  return props.modelValue[idx]?.metricName || props.modelValue[idx]?.metricCode || ''
}

function onMetricChange(idx: number, code: string) {
  const m = metrics.value.find((x) => x.code === code)
  if (!m) return
  const list = [...props.modelValue]
  list[idx] = {
    ...list[idx],
    metricCode: m.code,
    metricName: m.name,
    compareOp: m.defaultCompareOp || (m.valueType === 'state' ? 'EQ' : 'GT'),
    thresholdValue: m.defaultThreshold || (m.valueType === 'state' ? (m.options?.[0]?.value || '') : ''),
    thresholdUnit: m.unit
  }
  emit('update:modelValue', list)
  reco.value = undefined
}

function onAdd() {
  const m = metrics.value[0]
  const next: AlertRuleConditionItem = m
    ? {
        metricCode: m.code,
        metricName: m.name,
        compareOp: m.defaultCompareOp || 'GT',
        thresholdValue: m.defaultThreshold || '',
        thresholdUnit: m.unit
      }
    : { metricCode: '', metricName: '', compareOp: 'GT' }
  emit('update:modelValue', [...props.modelValue, next])
}

function onRemove(idx: number) {
  const list = [...props.modelValue]
  list.splice(idx, 1)
  emit('update:modelValue', list)
}

async function onAiHelperOpen(idx: number) {
  const cur = props.modelValue[idx]
  if (!cur?.metricCode || !props.objectType) return
  recoLoading.value = true
  reco.value = undefined
  try {
    reco.value = await recommendThreshold({
      objectType: props.objectType,
      metricCode: cur.metricCode,
      objectId: props.objectId
    })
  } finally {
    recoLoading.value = false
  }
}

function applyReco(idx: number, r: ThresholdRecoItem) {
  const list = [...props.modelValue]
  list[idx] = { ...list[idx], thresholdValue: String(r.value) }
  emit('update:modelValue', list)
}
</script>

<style scoped>
.condition-editor {
  display: grid;
  gap: 10px;
}

.logic-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logic-label {
  color: var(--text-muted);
  font-size: 12px;
}

.condition-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1.4fr) minmax(0, 1fr) minmax(0, 1.2fr) 30px;
  gap: 8px;
  align-items: center;
}

.row-index {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.18);
  color: #93C5FD;
  font-size: 12px;
  font-weight: 600;
}

.metric-select,
.op-select,
.threshold-input {
  width: 100%;
}

.threshold-wrap {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-helper-btn {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  border: 1px solid var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent);
  cursor: pointer;
  transition: all 0.15s ease;
  z-index: 1;
}

.ai-helper-btn:hover {
  background: var(--accent);
  color: var(--bg-base);
  border-color: var(--accent);
}

.threshold-wrap :deep(.el-input__wrapper) {
  padding-right: 36px;
}

.threshold-input.placeholder {
  display: grid;
  place-items: center;
  height: 32px;
  border: 1px dashed var(--line);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 12px;
}

.del-btn,
.add-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  background: var(--bg-elev-2);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s ease;
}

.del-btn {
  width: 30px;
  padding: 0;
}

.del-btn:hover:not(:disabled) {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-soft);
}

.add-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.del-btn:disabled,
.add-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.opt small {
  color: var(--text-muted);
}

/* AI 阈值推荐 popover */
.reco-pop {
  display: grid;
  gap: 10px;
  padding: 4px 2px;
}

.reco-head {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--accent);
  font-weight: 600;
  font-size: 13px;
}

.reco-head small {
  margin-left: auto;
  color: var(--text-muted);
  font-weight: 400;
  font-size: 11px;
}

.reco-list { display: grid; gap: 6px; }

.reco-item {
  display: grid;
  gap: 3px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.reco-item:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.reco-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reco-label span {
  color: var(--text-secondary);
  font-size: 12px;
}

.reco-label strong {
  color: var(--text-primary);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.reco-item small {
  color: var(--text-muted);
  font-size: 11px;
}

.reco-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  font-size: 11px;
  color: var(--text-muted);
}

.reco-stats b {
  color: var(--text-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.reco-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  padding: 8px 0;
}
</style>
