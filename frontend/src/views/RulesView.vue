<template>
  <div class="rules-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">告警规则 / RULES</span>
          <span class="dot-anim" />
          <span class="hero-time">差异化触发 · 多对象绑定 · AI 一句话建规则</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num">{{ stats?.total ?? 0 }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ (stats?.total ?? 0) > 0 ? '条规则在编排告警' : '尚未配置规则' }}</div>
            <div class="hero-line-2">
              启用 {{ stats?.enabled ?? 0 }} · 停用 {{ (stats?.total ?? 0) - (stats?.enabled ?? 0) }}
              <template v-for="lv in stats?.byLevel ?? []" :key="lv.alertLevel">
                · {{ lv.alertLevelName }} {{ lv.total }}
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <button class="hero-action ghost" @click="loadAll">
          <RefreshIcon :size="13" :stroke-width="1.6" /> 刷新
        </button>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.6" /> 新增规则
        </button>
      </div>
    </section>

    <!-- ========== AI BANNER (终端式 + shimmer) ========== -->
    <section class="ai-banner" @click="onAiClick">
      <div class="ai-banner-inner">
        <div class="ai-left">
          <div class="ai-eyebrow">
            <Sparkles :size="11" :stroke-width="1.8" />
            <span>AI 建规则 · BETA</span>
          </div>
          <div class="ai-title">用一句话描述你的告警需求</div>
          <div class="ai-terminal">
            <span class="prompt-mark">$</span>
            <span class="typewriter" :key="typingKey">{{ typingText }}</span>
            <span class="caret" />
          </div>
        </div>
        <div class="ai-right">
          <span class="ai-cta">
            打开 AI 控制台
            <ArrowRight :size="13" :stroke-width="1.8" />
          </span>
        </div>
      </div>
    </section>

    <!-- AI 对话框 -->
    <NlRuleDialog v-model="aiDialogVisible" @apply="onAiDraftApply" />

    <!-- ========== 级别分布条 ========== -->
    <section v-if="stats && stats.total > 0" class="level-bar-row">
      <div class="eyebrow inline">级别分布 / LEVEL</div>
      <div class="level-track">
        <div
          v-for="lv in stats.byLevel"
          :key="lv.alertLevel"
          class="level-seg"
          :style="{
            width: ((lv.total / stats.total) * 100) + '%',
            background: getAlertLevelMeta(lv.alertLevel).color
          }"
          :title="`${lv.alertLevelName} ${lv.total}`"
        />
      </div>
      <div class="level-legend">
        <span v-for="lv in stats.byLevel" :key="lv.alertLevel" class="lv-item">
          <span class="lv-dot" :style="{ background: getAlertLevelMeta(lv.alertLevel).color }" />
          <span class="lv-name">{{ lv.alertLevelName }}</span>
          <span class="lv-num tabular-nums">{{ lv.total }}</span>
        </span>
      </div>
    </section>

    <!-- ========== Toolbar ========== -->
    <section class="toolbar">
      <div class="seg">
        <button
          class="seg-item"
          :class="{ active: !filters.objectType }"
          @click="setType('')"
        >
          全部 <em class="tabular-nums">{{ stats?.total ?? 0 }}</em>
        </button>
        <button
          v-for="t in OBJECT_TYPES"
          :key="t.value"
          class="seg-item"
          :class="{ active: filters.objectType === t.value }"
          @click="setType(t.value)"
        >
          <component :is="t.icon" :size="12" :stroke-width="1.7" />
          {{ t.short }}
          <em class="tabular-nums">{{ countOf(t.value) }}</em>
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
          <template #prefix><SearchIcon :size="13" :stroke-width="1.7" /></template>
        </el-input>
      </div>
    </section>

    <!-- ========== Rule List ========== -->
    <section v-loading="loading && list.length > 0" class="rule-list">
      <SkeletonList v-if="loading && list.length === 0" :rows="6" />
      <article
        v-for="item in list"
        :key="item.id"
        class="rule-card"
        :class="{ disabled: item.status === 'DISABLED' }"
      >
        <span class="lv-strip" :style="{ background: getAlertLevelMeta(item.alertLevel).color }" />

        <div class="card-head">
          <div class="head-meta">
            <div class="title-row">
              <span class="rule-name">{{ item.ruleName }}</span>
              <span class="lv-tag" :style="{
                color: getAlertLevelMeta(item.alertLevel).color,
                borderColor: getAlertLevelMeta(item.alertLevel).color
              }">{{ item.alertLevelName }}</span>
            </div>
            <div class="sub-row">
              <span class="rule-code">{{ item.ruleCode }}</span>
              <span class="sep">·</span>
              <span class="type-tag">
                <component :is="getObjectTypeMeta(item.objectType).icon" :size="11" :stroke-width="1.7"
                  :style="{ color: getObjectTypeMeta(item.objectType).color }" />
                {{ item.objectTypeName }}
              </span>
              <span class="sep">·</span>
              <span :class="['st-pill', item.status === 'ENABLED' ? 'on' : 'off']">
                <span class="st-dot" />{{ item.status === 'ENABLED' ? '启用' : '停用' }}
              </span>
            </div>
          </div>

          <button class="toggle-btn" :class="{ on: item.status === 'ENABLED' }" @click="onToggle(item)">
            <span class="toggle-knob" />
          </button>
        </div>

        <div class="formula-block">
          <span class="formula-mark">▸</span>
          <code class="formula-text">{{ formulaOf(item) }}</code>
        </div>

        <div class="strategy">
          <span class="chip">连续 <b class="tabular-nums">{{ item.triggerTimes }}</b> 次</span>
          <span class="chip">窗口 <b class="tabular-nums">{{ item.timeWindowMinutes }}</b> min</span>
          <span class="chip">间隔 ≥ <b class="tabular-nums">{{ item.minAlertIntervalMinutes }}</b> min</span>
          <span v-if="item.recoverNotify" class="chip ok">恢复通知</span>
          <span v-if="item.repeatNotify" class="chip warn">重复通知</span>
        </div>

        <p v-if="item.description" class="desc" :title="item.description">{{ item.description }}</p>

        <footer class="card-actions">
          <button class="act-btn" @click="openDetail(item)"><EyeIcon :size="13" :stroke-width="1.7" />详情</button>
          <button class="act-btn" @click="openEdit(item)"><EditIcon :size="13" :stroke-width="1.7" />编辑</button>
          <el-popconfirm title="确认删除该规则？" confirm-button-text="删除" cancel-button-text="取消"
            @confirm="onDelete(item)">
            <template #reference>
              <button class="act-btn danger"><TrashIcon :size="13" :stroke-width="1.7" />删除</button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <div v-if="!loading && list.length === 0" class="empty">
        <BellOffIcon :size="28" :stroke-width="1.4" />
        <div class="empty-title">暂无告警规则</div>
        <div class="empty-hint">先添加监控对象，再为对象配置规则。或试试上面的「AI 一句话建规则」。</div>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.7" /> 新增规则
        </button>
      </div>
    </section>

    <!-- ========== 编辑对话框 ========== -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑告警规则' : '新增告警规则'"
      width="820px"
      append-to-body
      :close-on-click-modal="false"
      class="rule-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="section-title"><span class="num">01</span>基础信息</div>
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

        <div class="section-title"><span class="num">02</span>触发条件</div>
        <RuleConditionEditor
          v-model="form.conditions"
          v-model:logic="form.conditionLogic"
          :object-type="form.objectType"
          :object-id="form.objectIds?.length === 1 ? form.objectIds[0] : undefined"
        />

        <div class="section-title"><span class="num">03</span>触发策略</div>
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

        <div class="section-title"><span class="num">04</span>监控对象</div>
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
            <Lightbulb :size="12" :stroke-width="1.7" />
            该对象类型下还没有可用对象，先去
            <router-link to="/objects">监控对象</router-link>
            添加。
          </div>
        </el-form-item>

        <div class="section-title"><span class="num">05</span>通知渠道</div>
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
              <X :size="13" :stroke-width="1.8" />
            </button>
          </div>
          <button class="add-btn" type="button" @click="addBinding">
            <Plus :size="13" :stroke-width="1.8" />&nbsp;添加渠道
          </button>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存规则</el-button>
      </template>
    </el-dialog>

    <!-- ========== 详情对话框 ========== -->
    <el-dialog v-model="detailVisible" title="规则详情" width="720px" class="rule-detail-dialog">
      <div v-if="detail" class="detail-body">
        <div class="detail-head">
          <span class="lv-strip" :style="{ background: getAlertLevelMeta(detail.alertLevel).color }" />
          <div class="head-meta">
            <div class="title-row">
              <span class="rule-name">{{ detail.ruleName }}</span>
              <span class="lv-tag" :style="{
                color: getAlertLevelMeta(detail.alertLevel).color,
                borderColor: getAlertLevelMeta(detail.alertLevel).color
              }">{{ detail.alertLevelName }}</span>
            </div>
            <div class="sub-row">
              <span class="rule-code">{{ detail.ruleCode }}</span>
              <span class="sep">·</span>
              <span class="type-tag">{{ detail.objectTypeName }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">触发条件</div>
          <div class="formula-block">
            <span class="formula-mark">▸</span>
            <code class="formula-text">{{ formulaOf(detail) }}</code>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">监控对象 ({{ detail.objects?.length || 0 }})</div>
          <div class="brief-list">
            <span v-for="o in detail.objects" :key="o.id" class="brief-chip">
              <component :is="getObjectTypeMeta(o.objectType).icon" :size="11" :stroke-width="1.7"
                :style="{ color: getObjectTypeMeta(o.objectType).color }" />
              {{ o.objectName }}
            </span>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">通知渠道 ({{ detail.channels?.length || 0 }})</div>
          <div class="brief-list">
            <span v-for="c in detail.channels" :key="c.id" class="brief-chip">
              <component :is="getChannelTypeMeta(c.channelType).icon" :size="11" :stroke-width="1.7"
                :style="{ color: getChannelTypeMeta(c.channelType).color }" />
              {{ c.channelName }}
              <small v-if="c.receiverValue">→ {{ c.receiverValue }}</small>
            </span>
          </div>
        </div>

        <div class="detail-section">
          <div class="lbl">触发策略</div>
          <div class="strategy">
            <span class="chip">连续 <b class="tabular-nums">{{ detail.triggerTimes }}</b> 次</span>
            <span class="chip">窗口 <b class="tabular-nums">{{ detail.timeWindowMinutes }}</b> min</span>
            <span class="chip">间隔 ≥ <b class="tabular-nums">{{ detail.minAlertIntervalMinutes }}</b> min</span>
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
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus as PlusIcon,
  RefreshCw as RefreshIcon,
  Search as SearchIcon,
  Edit3 as EditIcon,
  Trash2 as TrashIcon,
  BellOff as BellOffIcon,
  Eye as EyeIcon,
  Plus,
  X,
  Sparkles,
  ArrowRight,
  Lightbulb
} from 'lucide-vue-next'
import RuleConditionEditor from '@/components/alert/RuleConditionEditor.vue'
import NlRuleDialog from '@/components/alert/NlRuleDialog.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import { getChannelTypeMeta } from '@/utils/channelType'
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

/* ========== 终端打字机示例 ========== */
const aiSamples = [
  '当生产 MySQL 主从延迟 > 5 分钟，连续 3 次，发企微到 DBA 群，紧急级别',
  '应用服务器 CPU 持续 10 分钟超过 85%，发邮件给运维',
  '客户信息同步作业失败 2 次以上，紧急通知数据团队',
  '数据加工作业输出条数为 0 时立即告警，发邮件 + 企微'
]
const typingText = ref('')
const typingKey = ref(0)
let typingTimer: ReturnType<typeof setTimeout> | null = null
let sampleIdx = 0
function startTyping() {
  const sample = aiSamples[sampleIdx % aiSamples.length]
  typingText.value = ''
  typingKey.value++
  let i = 0
  const tick = () => {
    if (i < sample.length) {
      typingText.value += sample.charAt(i)
      i++
      typingTimer = setTimeout(tick, 38)
    } else {
      typingTimer = setTimeout(() => {
        sampleIdx++
        startTyping()
      }, 2400)
    }
  }
  tick()
}

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
  initConditionsForType(form.objectType)
  form.objectIds = []
}

function addBinding() {
  if (!form.channelBindings) form.channelBindings = []
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

async function onAiDraftApply(draft: AlertRuleItem) {
  Object.assign(form, emptyForm(), {
    ...draft,
    status: draft.status || 'ENABLED'
  })
  if (!form.conditions?.length) {
    initConditionsForType(form.objectType)
  }
  aiDialogVisible.value = false
  dialogVisible.value = false
  await nextTick()
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
  if (val === old || !old) return
})

onMounted(() => {
  loadAll()
  startTyping()
})

onUnmounted(() => {
  if (typingTimer) clearTimeout(typingTimer)
})
</script>


<style scoped>
.rules-v {
  display: grid;
  gap: 22px;
  padding: 0 28px 32px;
  animation: fade-up 0.35s ease both;
}

/* ========== HERO ========== */
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding: 28px 0;
  border-bottom: 1px solid var(--line);
  position: relative;
}

.hero::before {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 80px;
  height: 1px;
  background: var(--accent);
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.dot-anim {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ok);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.hero-time {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.hero-headline {
  display: flex;
  align-items: flex-end;
  gap: 28px;
}

.hero-num {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 84px;
  letter-spacing: -0.05em;
  line-height: 0.85;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.hero-words { padding-bottom: 6px; }

.hero-line-1 {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.hero-line-2 {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.hero-right {
  display: inline-flex;
  gap: 10px;
}

.hero-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.hero-action.ghost:hover {
  border-color: var(--accent-line);
  color: var(--accent);
}

.hero-action.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--bg-base);
  font-weight: 500;
}

.hero-action.primary:hover {
  filter: brightness(1.08);
}

/* ========== AI BANNER (终端风) ========== */
.ai-banner {
  position: relative;
  cursor: pointer;
  padding: 1px;
  border-radius: var(--radius-md);
  background: linear-gradient(120deg,
    var(--accent-line) 0%,
    transparent 30%,
    var(--accent) 50%,
    transparent 70%,
    var(--accent-line) 100%);
  background-size: 200% 100%;
  animation: shimmer 6s linear infinite;
  transition: transform 0.2s ease;
}

.ai-banner:hover { transform: translateY(-1px); }

.ai-banner-inner {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 24px;
  padding: 18px 22px;
  border-radius: calc(var(--radius-md) - 1px);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
}

.ai-left { display: grid; gap: 8px; min-width: 0; }

.ai-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: max-content;
  padding: 3px 10px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.18em;
  background: var(--accent-soft);
}

.ai-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.ai-terminal {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
}

.prompt-mark {
  color: var(--accent);
  font-weight: 600;
}

.typewriter {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.caret {
  display: inline-block;
  width: 7px;
  height: 14px;
  background: var(--accent);
  animation: blink 1s steps(2) infinite;
  flex-shrink: 0;
}

.ai-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border-radius: 999px;
  background: var(--accent);
  color: var(--bg-base);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

/* ========== Level distribution ========== */
.level-bar-row {
  display: grid;
  grid-template-columns: max-content 1fr;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
}

.eyebrow.inline { margin: 0; }

.level-track {
  display: flex;
  height: 6px;
  border-radius: 3px;
  overflow: hidden;
  background: var(--bg-elev-3);
}

.level-seg { transition: width 0.4s ease; }
.level-seg + .level-seg { border-left: 1px solid var(--bg-elev-1); }

.level-legend {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding-top: 4px;
  border-top: 1px dashed var(--line);
}

.lv-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.lv-dot {
  width: 7px;
  height: 7px;
  border-radius: 1.5px;
}

.lv-name { color: var(--text-secondary); }
.lv-num { color: var(--text-primary); font-weight: 500; }

/* ========== Toolbar ========== */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.seg {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-1);
}

.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.seg-item:hover { color: var(--text-primary); }

.seg-item.active {
  background: var(--accent-soft);
  color: var(--accent);
}

.seg-item em {
  margin-left: 2px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--bg-elev-3);
  color: var(--text-secondary);
  font-style: normal;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.seg-item.active em {
  background: var(--accent);
  color: var(--bg-base);
}

.filter-row { display: flex; gap: 8px; }
.filter-select { width: 130px; }
.filter-input { width: 240px; }

/* ========== Rule List ========== */
.rule-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 14px;
  min-height: 240px;
}

.rule-card {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 18px 20px 14px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.rule-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
}

.rule-card.disabled {
  opacity: 0.55;
}

.lv-strip {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.head-meta { flex: 1; min-width: 0; display: grid; gap: 6px; }

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rule-name {
  font-family: var(--font-display);
  font-size: 15.5px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.lv-tag {
  padding: 1px 8px;
  border: 1px solid;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.sub-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.rule-code { color: var(--text-secondary); }

.sep { color: var(--text-faint); }

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
}

.st-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 8px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.st-pill.on  { background: var(--ok-soft);     color: var(--ok); }
.st-pill.off { background: var(--bg-elev-3);   color: var(--text-muted); }

.st-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.st-pill.on .st-dot {
  animation: pulse-soft 2s ease-in-out infinite;
}

/* Toggle switch */
.toggle-btn {
  flex-shrink: 0;
  width: 36px;
  height: 20px;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--bg-elev-3);
  cursor: pointer;
  position: relative;
  transition: all 0.18s ease;
}

.toggle-knob {
  position: absolute;
  top: 1px;
  left: 1px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: all 0.18s ease;
}

.toggle-btn.on {
  background: var(--accent);
  border-color: var(--accent);
}

.toggle-btn.on .toggle-knob {
  left: 17px;
  background: var(--bg-base);
}

/* Formula */
.formula-block {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
}

.formula-mark {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 12px;
  flex-shrink: 0;
}

.formula-text {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-primary);
  word-break: break-all;
  line-height: 1.5;
}

/* Strategy chips */
.strategy {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 11px;
}

.chip b { color: var(--text-primary); font-weight: 500; }

.chip.ok {
  border-color: rgba(52, 211, 153, 0.3);
  background: var(--ok-soft);
  color: var(--ok);
}

.chip.warn {
  border-color: rgba(251, 191, 36, 0.3);
  background: var(--warn-soft);
  color: var(--warn);
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

.card-actions {
  display: flex;
  gap: 4px;
  margin-top: 2px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.act-btn:hover {
  background: var(--bg-elev-2);
  color: var(--text-primary);
}

.act-btn.danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

/* Empty */
.empty {
  grid-column: 1 / -1;
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 60px 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-md);
  color: var(--text-muted);
}

.empty-title {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.empty-hint { font-size: 12px; margin-bottom: 8px; }

/* ========== Dialog ========== */
:deep(.rule-dialog .el-dialog),
:deep(.rule-detail-dialog .el-dialog) {
  background: var(--bg-elev-1);
  border: 1px solid var(--line);
  box-shadow: var(--inset);
}

:deep(.rule-dialog .el-dialog__title),
:deep(.rule-detail-dialog .el-dialog__title) {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
  font-family: var(--font-display);
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.section-title:first-child { margin-top: 0; }

.section-title .num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

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
  font-family: var(--font-mono);
  font-size: 11px;
}

.hint-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: var(--warn);
  font-size: 12px;
}

.hint-row a { color: var(--accent); margin: 0 4px; }

/* Channel binding */
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
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  background: var(--bg-elev-2);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s ease;
}

.del-btn { width: 30px; padding: 0; }

.del-btn:hover {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-soft);
}

.add-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

/* ========== Detail dialog ========== */
.detail-body { display: grid; gap: 14px; }

.detail-head {
  position: relative;
  display: flex;
  gap: 14px;
  padding: 14px 16px 14px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
}

.detail-head .lv-strip {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  border-radius: 3px 0 0 3px;
}

.detail-section {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
}

.detail-section .lbl {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.brief-list { display: flex; flex-wrap: wrap; gap: 6px; }

.brief-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-size: 12px;
}

.brief-chip small {
  color: var(--text-muted);
  margin-left: 2px;
  font-family: var(--font-mono);
}

.desc-text {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

/* Responsive */
@media (max-width: 760px) {
  .rules-v { padding: 0 14px 24px; }
  .hero-num { font-size: 64px; }
  .ai-banner-inner { grid-template-columns: 1fr; }
  .ai-cta { width: max-content; }
  .form-grid,
  .strategy-grid { grid-template-columns: 1fr; }
  .span-2, .span-3 { grid-column: span 1; }
}
</style>
