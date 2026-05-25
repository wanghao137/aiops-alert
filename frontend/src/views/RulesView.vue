<template>
  <div class="rules-view">
    <PageHeader
      eyebrow="ALERT RULES"
      title="告警规则管理"
      subtitle="为不同对象类型配置差异化告警规则。支持多条件 AND/OR、连续触发次数、最小告警间隔。AI 一句话生成规则即将上线。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增规则</el-button>
      </template>
    </PageHeader>

    <!-- AI 入口横幅 -->
    <section class="ai-banner" @click="onAiClick">
      <div class="ai-bar">
        <div class="ai-icon">
          <Sparkles :size="18" />
        </div>
        <div class="ai-content">
          <div class="ai-title">用一句话描述你的告警规则</div>
          <div class="ai-hint">例如："当生产 MySQL 主从延迟超过 5 分钟、连续触发 3 次，发企微到 DBA 群，紧急级别"</div>
        </div>
        <div class="ai-cta">
          AI 建规则
          <ArrowRight :size="14" />
        </div>
      </div>
    </section>

    <!-- AI 对话框 -->
    <NlRuleDialog v-model="aiDialogVisible" @apply="onAiDraftApply" />

    <!-- 顶部统计 -->
    <section class="stat-row">
      <StatCard label="规则总数" :value="stats?.total ?? 0" :icon="LayersIcon" accent="#3B82F6"
        :hint="`其中 ${stats?.enabled ?? 0} 个启用`" />
      <StatCard
        v-for="lv in stats?.byLevel ?? []"
        :key="lv.alertLevel"
        :label="lv.alertLevelName"
        :value="lv.total"
        :icon="getAlertLevelMeta(lv.alertLevel).icon"
        :accent="getAlertLevelMeta(lv.alertLevel).color"
        hint="按级别分布"
      />
    </section>

    <!-- 筛选 -->
    <section class="toolbar">
      <div class="type-tabs">
        <button class="type-tab" :class="{ active: !filters.objectType }" @click="setType('')">
          全部 <em>{{ stats?.total ?? 0 }}</em>
        </button>
        <button
          v-for="t in OBJECT_TYPES"
          :key="t.value"
          class="type-tab"
          :class="{ active: filters.objectType === t.value }"
          :style="filters.objectType === t.value ? { borderColor: t.color, color: t.color } : {}"
          @click="setType(t.value)"
        >
          <component :is="t.icon" :size="14" />
          {{ t.short }}
          <em>{{ countOf(t.value) }}</em>
        </button>
      </div>
      <div class="filter-row">
        <el-select v-model="filters.alertLevel" placeholder="级别" clearable class="filter-select" @change="loadList">
          <el-option v-for="lv in ALERT_LEVELS" :key="lv.value" :label="lv.label" :value="lv.value" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable class="filter-select" @change="loadList">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索名称 / 编码 / 描述" clearable class="filter-input"
          @keyup.enter="loadList" @clear="loadList">
          <template #prefix><SearchIcon :size="14" /></template>
        </el-input>
      </div>
    </section>

    <!-- 列表 -->
    <section v-loading="loading" class="card-grid"
      :style="{ minHeight: list.length ? 'auto' : '240px' }">
      <article
        v-for="item in list"
        :key="item.id"
        class="rule-card"
        :class="{ disabled: item.status === 'DISABLED' }"
      >
        <header class="head">
          <div class="level-bar" :style="{ background: getAlertLevelMeta(item.alertLevel).color }" />
          <div class="meta">
            <div class="title-row">
              <span class="rule-name" :title="item.ruleName">{{ item.ruleName }}</span>
              <span class="level-pill" :style="{
                background: getAlertLevelMeta(item.alertLevel).bg,
                color: getAlertLevelMeta(item.alertLevel).color
              }">
                <component :is="getAlertLevelMeta(item.alertLevel).icon" :size="11" />
                {{ item.alertLevelName }}
              </span>
            </div>
            <div class="sub-row">
              <span class="code">{{ item.ruleCode }}</span>
              <span class="dot-sep" />
              <span class="type-tag">
                <component :is="getObjectTypeMeta(item.objectType).icon" :size="11" />
                {{ item.objectTypeName }}
              </span>
              <span class="dot-sep" />
              <span :class="['status-mini', item.status === 'ENABLED' ? 'on' : 'off']">
                {{ item.status === 'ENABLED' ? '启用' : '停用' }}
              </span>
            </div>
          </div>
        </header>

        <div class="formula">
          <span class="formula-label">触发条件</span>
          <code class="formula-text">{{ formulaOf(item) }}</code>
        </div>

        <div class="strategy">
          <span class="chip">连续 {{ item.triggerTimes }} 次</span>
          <span class="chip">窗口 {{ item.timeWindowMinutes }} min</span>
          <span class="chip">间隔 ≥ {{ item.minAlertIntervalMinutes }} min</span>
          <span v-if="item.recoverNotify" class="chip ok">恢复通知</span>
          <span v-if="item.repeatNotify" class="chip warn">重复通知</span>
        </div>

        <p v-if="item.description" class="desc" :title="item.description">{{ item.description }}</p>

        <footer class="actions">
          <el-button text @click="openDetail(item)"><EyeIcon :size="14" />&nbsp;详情</el-button>
          <el-button text @click="openEdit(item)"><EditIcon :size="14" />&nbsp;编辑</el-button>
          <el-button text @click="onToggle(item)">
            <PowerIcon :size="14" />&nbsp;{{ item.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
          <el-popconfirm title="确认删除该规则？" confirm-button-text="删除" cancel-button-text="取消"
            @confirm="onDelete(item)">
            <template #reference>
              <el-button text type="danger"><TrashIcon :size="14" />&nbsp;删除</el-button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <div v-if="!loading && list.length === 0" class="empty">
        <div class="empty-icon"><BellOffIcon :size="36" /></div>
        <div class="empty-title">暂无告警规则</div>
        <div class="empty-hint">先添加监控对象，再为对象配置规则。或试试上面的"AI 一句话建规则"。</div>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增规则</el-button>
      </div>
    </section>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑告警规则' : '新增告警规则'"
      width="820px"
      :close-on-click-modal="false"
      class="rule-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="section-title">基础信息</div>
        <div class="form-grid">
          <el-form-item label="规则名称" prop="ruleName" class="span-2">
            <el-input v-model="form.ruleName" placeholder="例如：生产 MySQL 主从延迟告警" />
          </el-form-item>
          <el-form-item label="对象类型" prop="objectType">
            <el-select v-model="form.objectType" class="full" @change="onObjectTypeChange">
              <el-option v-for="t in OBJECT_TYPES" :key="t.value" :label="t.label" :value="t.value">
                <span class="opt-row">
                  <component :is="t.icon" :size="14" :style="{ color: t.color }" />
                  {{ t.label }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="告警级别" prop="alertLevel">
            <el-select v-model="form.alertLevel" class="full">
              <el-option v-for="lv in ALERT_LEVELS" :key="lv.value" :label="lv.label" :value="lv.value">
                <span class="opt-row">
                  <component :is="lv.icon" :size="14" :style="{ color: lv.color }" />
                  {{ lv.label }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="规则编码">
            <el-input v-model="form.ruleCode" placeholder="留空自动生成" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="1" :max="999" controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="规则描述" class="span-3">
            <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </div>

        <div class="section-title">触发条件</div>
        <RuleConditionEditor
          v-model="form.conditions"
          v-model:logic="form.conditionLogic"
          :object-type="form.objectType"
          :object-id="form.objectIds?.length === 1 ? form.objectIds[0] : undefined"
        />

        <div class="section-title">触发策略</div>
        <div class="form-grid strategy-grid">
          <el-form-item label="连续触发次数">
            <el-input-number v-model="form.triggerTimes" :min="1" :max="100" controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="观察窗口 (分钟)">
            <el-input-number v-model="form.timeWindowMinutes" :min="1" :max="1440" controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="最小告警间隔 (分钟)">
            <el-input-number v-model="form.minAlertIntervalMinutes" :min="0" :max="1440" controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="通知策略" class="span-3">
            <el-checkbox v-model="form.recoverNotify">恢复时也通知</el-checkbox>
            <el-checkbox v-model="form.repeatNotify">未恢复时重复通知</el-checkbox>
          </el-form-item>
        </div>

        <div class="section-title">监控对象</div>
        <el-form-item prop="objectIds">
          <el-select
            v-model="form.objectIds"
            multiple
            filterable
            placeholder="选择该规则要应用到的监控对象（可多选）"
            class="full"
          >
            <el-option
              v-for="o in availableObjects"
              :key="o.id"
              :label="o.objectName"
              :value="o.id!"
            >
              <span class="opt-row">
                <component :is="getObjectTypeMeta(o.objectType).icon" :size="14"
                  :style="{ color: getObjectTypeMeta(o.objectType).color }" />
                {{ o.objectName }}
                <small class="opt-code">{{ o.objectCode }}</small>
              </span>
            </el-option>
          </el-select>
          <div v-if="!availableObjects.length && form.objectType" class="hint-row">
            <Lightbulb :size="13" />
            该对象类型下还没有可用对象，先去
            <router-link to="/objects">监控对象</router-link>
            添加。
          </div>
        </el-form-item>

        <div class="section-title">通知渠道</div>
        <div class="channel-binding">
          <div v-for="(b, idx) in form.channelBindings" :key="idx" class="binding-row">
            <el-select v-model="b.channelId" placeholder="选择渠道" class="binding-channel">
              <el-option
                v-for="c in availableChannels"
                :key="c.id"
                :label="c.channelName"
                :value="c.id!"
                :disabled="!!form.channelBindings?.find((x, i) => i !== idx && x.channelId === c.id)"
              >
                <span class="opt-row">
                  <component :is="getChannelTypeMeta(c.channelType).icon" :size="14"
                    :style="{ color: getChannelTypeMeta(c.channelType).color }" />
                  {{ c.channelName }}
                  <small class="opt-code">{{ c.channelTypeName }}</small>
                </span>
              </el-option>
            </el-select>
            <el-input v-model="b.receiverValue" placeholder="接收人（手机号/邮箱列表，留空使用渠道默认）" class="binding-receiver" />
            <button class="del-btn" type="button" @click="removeBinding(idx)">
              <X :size="14" />
            </button>
          </div>
          <button class="add-btn" type="button" @click="addBinding">
            <Plus :size="14" />&nbsp;添加渠道
          </button>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存规则</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="规则详情" width="720px">
      <div v-if="detail" class="detail-body">
        <div class="detail-head">
          <div class="level-bar" :style="{ background: getAlertLevelMeta(detail.alertLevel).color }" />
          <div>
            <div class="title-row">
              <span class="rule-name">{{ detail.ruleName }}</span>
              <span class="level-pill" :style="{
                background: getAlertLevelMeta(detail.alertLevel).bg,
                color: getAlertLevelMeta(detail.alertLevel).color
              }">
                <component :is="getAlertLevelMeta(detail.alertLevel).icon" :size="11" />
                {{ detail.alertLevelName }}
              </span>
            </div>
            <div class="sub-row">
              <span class="code">{{ detail.ruleCode }}</span>
              <span class="dot-sep" />
              <span class="type-tag">{{ detail.objectTypeName }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">触发条件</div>
          <code class="formula-text">{{ formulaOf(detail) }}</code>
        </div>

        <div class="detail-section">
          <div class="lbl">监控对象 ({{ detail.objects?.length || 0 }})</div>
          <div class="brief-list">
            <span v-for="o in detail.objects" :key="o.id" class="brief-chip">
              <component :is="getObjectTypeMeta(o.objectType).icon" :size="11"
                :style="{ color: getObjectTypeMeta(o.objectType).color }" />
              {{ o.objectName }}
            </span>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">通知渠道 ({{ detail.channels?.length || 0 }})</div>
          <div class="brief-list">
            <span v-for="c in detail.channels" :key="c.id" class="brief-chip">
              <component :is="getChannelTypeMeta(c.channelType).icon" :size="11"
                :style="{ color: getChannelTypeMeta(c.channelType).color }" />
              {{ c.channelName }}
              <small v-if="c.receiverValue">→ {{ c.receiverValue }}</small>
            </span>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">触发策略</div>
          <div class="strategy">
            <span class="chip">连续 {{ detail.triggerTimes }} 次</span>
            <span class="chip">窗口 {{ detail.timeWindowMinutes }} min</span>
            <span class="chip">间隔 ≥ {{ detail.minAlertIntervalMinutes }} min</span>
            <span v-if="detail.recoverNotify" class="chip ok">恢复通知</span>
            <span v-if="detail.repeatNotify" class="chip warn">重复通知</span>
          </div>
        </div>

        <div v-if="detail.description" class="detail-section">
          <div class="lbl">描述</div>
          <div class="desc-text">{{ detail.description }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus as PlusIcon,
  RefreshCw as RefreshIcon,
  Search as SearchIcon,
  Edit3 as EditIcon,
  Power as PowerIcon,
  Trash2 as TrashIcon,
  Layers as LayersIcon,
  BellOff as BellOffIcon,
  Eye as EyeIcon,
  Plus,
  X,
  Sparkles,
  ArrowRight,
  Lightbulb
} from 'lucide-vue-next'
import StatCard from '@/components/common/StatCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import RuleConditionEditor from '@/components/alert/RuleConditionEditor.vue'
import NlRuleDialog from '@/components/alert/NlRuleDialog.vue'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import { CHANNEL_TYPES, getChannelTypeMeta } from '@/utils/channelType'
import { ALERT_LEVELS, getAlertLevelMeta } from '@/utils/alertLevel'
import { useCatalogStore } from '@/stores/catalog'
import {
  deleteAlertRule,
  getAlertRule,
  getAlertRuleStats,
  listAlertRules,
  saveAlertRule,
  toggleAlertRule,
  type AlertRuleItem,
  type AlertRuleStats
} from '@/api/alertRule'
import { listMonitorObjects, type MonitorObjectItem } from '@/api/monitorObject'
import { listAlertChannels, type AlertChannelItem } from '@/api/alertChannel'

const catalog = useCatalogStore()
const list = ref<AlertRuleItem[]>([])
const stats = ref<AlertRuleStats>()
const allObjects = ref<MonitorObjectItem[]>([])
const allChannels = ref<AlertChannelItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<AlertRuleItem>()
const formRef = ref<FormInstance>()

const filters = reactive({ objectType: '', alertLevel: '', status: '', keyword: '' })
const aiDialogVisible = ref(false)

const emptyForm = (): AlertRuleItem => ({
  ruleName: '',
  ruleCode: '',
  objectType: 'SERVER',
  conditionLogic: 'AND',
  triggerTimes: 1,
  timeWindowMinutes: 5,
  minAlertIntervalMinutes: 30,
  alertLevel: 'NORMAL',
  recoverNotify: true,
  repeatNotify: false,
  status: 'ENABLED',
  priority: 100,
  description: '',
  conditions: [],
  objectIds: [],
  channelBindings: []
})

const form = reactive<AlertRuleItem>(emptyForm())

const rules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  objectType: [{ required: true, message: '请选择对象类型', trigger: 'change' }],
  alertLevel: [{ required: true, message: '请选择告警级别', trigger: 'change' }],
  objectIds: [{
    required: true, validator: (_r, v, cb) =>
      Array.isArray(v) && v.length ? cb() : cb(new Error('至少选择一个监控对象')),
    trigger: 'change'
  }]
}

const availableObjects = computed(() =>
  allObjects.value.filter((o) => o.objectType === form.objectType)
)
const availableChannels = computed(() => allChannels.value)

function countOf(type: string) {
  return stats.value?.byType.find((t) => t.objectType === type)?.total ?? 0
}

async function loadList() {
  loading.value = true
  try {
    list.value = await listAlertRules({
      objectType: filters.objectType || undefined,
      alertLevel: filters.alertLevel || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  stats.value = await getAlertRuleStats()
}

async function loadDeps() {
  const [objs, chs] = await Promise.all([
    listMonitorObjects({ status: 'ENABLED' }),
    listAlertChannels({ status: 'ENABLED' })
  ])
  allObjects.value = objs
  allChannels.value = chs
}

async function loadAll() {
  await catalog.ensureLoaded()
  await Promise.all([loadList(), loadStats(), loadDeps()])
}

function setType(type: string) {
  filters.objectType = type
  loadList()
}

function openCreate() {
  Object.assign(form, emptyForm())
  // 默认带一个条件占位
  initConditionsForType(form.objectType)
  dialogVisible.value = true
}

async function openEdit(item: AlertRuleItem) {
  if (!item.id) return
  const full = await getAlertRule(item.id)
  Object.assign(form, emptyForm(), full)
  if (!form.conditions?.length) {
    initConditionsForType(form.objectType)
  }
  dialogVisible.value = true
}

async function openDetail(item: AlertRuleItem) {
  if (!item.id) return
  detail.value = await getAlertRule(item.id)
  detailVisible.value = true
}

function initConditionsForType(objectType: string) {
  const metric = catalog.metricsOfType(objectType)[0]
  if (!metric) {
    form.conditions = []
    return
  }
  form.conditions = [{
    metricCode: metric.code,
    metricName: metric.name,
    compareOp: metric.defaultCompareOp || 'GT',
    thresholdValue: metric.defaultThreshold || '',
    thresholdUnit: metric.unit
  }]
}

function onObjectTypeChange() {
  // 切换对象类型时清空条件 + 对象选择
  initConditionsForType(form.objectType)
  form.objectIds = []
}

function addBinding() {
  if (!form.channelBindings) form.channelBindings = []
  // 找一个还没绑过的渠道
  const used = new Set(form.channelBindings.map((b) => b.channelId))
  const next = allChannels.value.find((c) => c.id && !used.has(c.id))
  if (!next?.id) {
    ElMessage.warning('暂无可绑定的渠道，先去通知渠道管理添加')
    return
  }
  form.channelBindings.push({ channelId: next.id, receiverValue: '' })
}

function removeBinding(idx: number) {
  form.channelBindings?.splice(idx, 1)
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.conditions?.length) {
    ElMessage.error('至少配置一个触发条件')
    return
  }
  submitting.value = true
  try {
    await saveAlertRule({ ...form })
    ElMessage.success(form.id ? '规则已更新' : '规则已创建')
    dialogVisible.value = false
    await loadAll()
  } finally {
    submitting.value = false
  }
}

async function onToggle(item: AlertRuleItem) {
  if (!item.id) return
  await toggleAlertRule(item.id)
  ElMessage.success('已切换状态')
  await loadAll()
}

async function onDelete(item: AlertRuleItem) {
  if (!item.id) return
  await deleteAlertRule(item.id)
  ElMessage.success('规则已删除')
  await loadAll()
}

function onAiClick() {
  aiDialogVisible.value = true
}

function onAiDraftApply(draft: AlertRuleItem) {
  // 把 AI 草稿合并到表单：保留默认值、AI 缺失的字段不会被覆盖
  Object.assign(form, emptyForm(), {
    ...draft,
    // 强制启用，方便用户保存
    status: draft.status || 'ENABLED'
  })
  if (!form.conditions?.length) {
    initConditionsForType(form.objectType)
  }
  dialogVisible.value = true
}

function formulaOf(rule: AlertRuleItem) {
  if (!rule.conditions?.length) return '-'
  const parts = rule.conditions.map((c) => {
    const op = catalog.findCompareOp(c.compareOp)
    const sym = op?.symbol || c.compareOp
    if (op?.inputKind === 'state') {
      return `${c.metricName} ${sym}`
    }
    return `${c.metricName} ${sym} ${c.thresholdValue || ''}${c.thresholdUnit || ''}`
  })
  const sep = rule.conditionLogic === 'OR' ? ' OR ' : ' AND '
  return parts.join(sep).trim()
}

watch(() => form.objectType, (val, old) => {
  // 编辑模式下首次加载会触发一次，跳过
  if (val === old || !old) return
})

onMounted(loadAll)
</script>

<style scoped>
.rules-view {
  display: grid;
  gap: 16px;
}

.ai-banner {
  cursor: pointer;
  border-radius: 12px;
  padding: 1px;
  background: linear-gradient(120deg, rgba(59, 130, 246, 0.5), rgba(139, 92, 246, 0.5), rgba(245, 158, 11, 0.5));
  background-size: 200% 200%;
  animation: shimmer 8s linear infinite;
}

@keyframes shimmer {
  0%   { background-position:   0% 50%; }
  50%  { background-position: 100% 50%; }
  100% { background-position:   0% 50%; }
}

.ai-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 11px;
  background: var(--bg-panel);
}

.ai-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #3B82F6, #8B5CF6);
  color: white;
  flex-shrink: 0;
}

.ai-content {
  flex: 1;
  min-width: 0;
}

.ai-title {
  color: var(--text-primary);
  font-weight: 600;
  font-size: 14px;
}

.ai-hint {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: linear-gradient(135deg, #3B82F6, #8B5CF6);
  color: white;
  font-size: 12px;
  font-weight: 600;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1280px) {
  .stat-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 760px) {
  .stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
  flex-wrap: wrap;
}

.type-tabs { display: flex; gap: 6px; flex-wrap: wrap; }

.type-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.type-tab:hover { color: var(--text-primary); border-color: var(--line-subtle); }

.type-tab.active {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(59, 130, 246, 0.08);
}

.type-tab em {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-style: normal;
  font-size: 11px;
}

.filter-row { display: flex; gap: 8px; }
.filter-select { width: 130px; }
.filter-input { width: 240px; }

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
}

.rule-card {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.rule-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-subtle);
  box-shadow: 0 16px 30px -20px rgba(0, 0, 0, 0.6);
}

.rule-card.disabled {
  opacity: 0.6;
}

.head {
  display: flex;
  gap: 12px;
}

.level-bar {
  width: 4px;
  border-radius: 4px;
  flex-shrink: 0;
}

.meta { flex: 1; min-width: 0; }

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.rule-name {
  flex: 1;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.level-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 11px;
  flex-wrap: wrap;
}

.code { font-family: 'JetBrains Mono', monospace; }

.dot-sep {
  width: 3px;
  height: 3px;
  background: var(--text-muted);
  border-radius: 50%;
  opacity: 0.5;
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.status-mini.on { color: #6EE7B7; }
.status-mini.off { color: var(--text-muted); }

.formula {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
  display: grid;
  gap: 4px;
}

.formula-label {
  color: var(--text-muted);
  font-size: 11px;
}

.formula-text {
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12.5px;
  white-space: pre-wrap;
  word-break: break-all;
}

.strategy { display: flex; flex-wrap: wrap; gap: 5px; }

.chip {
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 11px;
}

.chip.ok {
  border-color: rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.1);
  color: #6EE7B7;
}

.chip.warn {
  border-color: rgba(245, 158, 11, 0.3);
  background: rgba(245, 158, 11, 0.1);
  color: #FCD34D;
}

.desc {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.actions {
  display: flex;
  gap: 4px;
  margin-top: auto;
  padding-top: 6px;
}

.empty {
  grid-column: 1 / -1;
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 60px 20px;
  border: 1px dashed var(--line-subtle);
  border-radius: 12px;
  text-align: center;
}

.empty-icon {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--bg-subtle);
  color: var(--text-muted);
}

.empty-title {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.empty-hint { color: var(--text-muted); margin-bottom: 8px; }

.section-title {
  margin: 16px 0 10px;
  padding: 6px 10px;
  border-left: 3px solid var(--accent);
  background: var(--bg-subtle);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.section-title:first-child { margin-top: 0; }

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 16px;
}

.strategy-grid { grid-template-columns: 1fr 1fr 1fr; }

.span-2 { grid-column: span 2; }
.span-3 { grid-column: span 3; }

.full { width: 100%; }

.opt-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.opt-code {
  margin-left: auto;
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}

.hint-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: #FCD34D;
  font-size: 12px;
}

.hint-row a { color: var(--accent); margin: 0 4px; }

.channel-binding { display: grid; gap: 8px; }

.binding-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr) 30px;
  gap: 8px;
  align-items: center;
}

.binding-channel,
.binding-receiver { width: 100%; }

.del-btn,
.add-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 10px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--bg-subtle);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s ease;
}

.del-btn { width: 30px; padding: 0; }

.del-btn:hover {
  border-color: rgba(239, 68, 68, 0.4);
  color: #FCA5A5;
  background: rgba(239, 68, 68, 0.1);
}

.add-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(59, 130, 246, 0.08);
}

.detail-body { display: grid; gap: 16px; }

.detail-head { display: flex; gap: 12px; align-items: stretch; }
.detail-head .level-bar { width: 3px; border-radius: 3px; }

.detail-section {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
  display: grid;
  gap: 8px;
}

.detail-section .lbl {
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0.4px;
}

.brief-list { display: flex; flex-wrap: wrap; gap: 6px; }

.brief-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
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

.desc-text {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
</style>
