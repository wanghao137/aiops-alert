<template>
  <div class="channels-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">ALERT CHANNELS</span>
          <span class="dot-anim" />
          <span class="hero-time">企业微信 · 邮件 · 短信</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num">{{ stats?.total ?? 0 }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ (stats?.total ?? 0) > 0 ? '条通知通道随时待命' : '尚未配置任何渠道' }}</div>
            <div class="hero-line-2">
              启用 {{ stats?.enabled ?? 0 }} · 今日成功 {{ stats?.sentToday ?? 0 }} · 今日失败 {{ stats?.failedToday ?? 0 }}
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <button class="hero-action ghost" @click="loadAll">
          <RefreshIcon :size="13" :stroke-width="1.6" /> 刷新
        </button>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.6" /> 新增渠道
        </button>
      </div>
    </section>

    <!-- ========== 发送统计带 ========== -->
    <section class="stat-bar">
      <article class="stat-cell">
        <div class="stat-head">
          <span class="eyebrow inline">TOTAL CHANNELS</span>
        </div>
        <div class="stat-num tabular-nums">{{ stats?.total ?? 0 }}</div>
        <div class="stat-meta">启用 {{ stats?.enabled ?? 0 }} · 停用 {{ (stats?.total ?? 0) - (stats?.enabled ?? 0) }}</div>
      </article>
      <article class="stat-cell ok">
        <div class="stat-head">
          <span class="eyebrow inline">DELIVERED · TODAY</span>
          <CheckIcon :size="13" :stroke-width="1.7" />
        </div>
        <div class="stat-num tabular-nums">{{ stats?.sentToday ?? 0 }}</div>
        <div class="stat-meta">所有通道累计</div>
      </article>
      <article class="stat-cell danger">
        <div class="stat-head">
          <span class="eyebrow inline">FAILED · TODAY</span>
          <AlertIcon :size="13" :stroke-width="1.7" />
        </div>
        <div class="stat-num tabular-nums">{{ stats?.failedToday ?? 0 }}</div>
        <div class="stat-meta">可在事件详情重发</div>
      </article>
      <article
        v-for="t in stats?.byType ?? []"
        :key="t.channelType"
        class="stat-cell type"
        :class="{ active: filters.channelType === t.channelType }"
        @click="setType(filters.channelType === t.channelType ? '' : t.channelType)"
      >
        <div class="stat-head">
          <span class="eyebrow inline">{{ t.channelTypeName }}</span>
          <span class="type-dot" :style="{ background: getChannelTypeMeta(t.channelType).color }" />
        </div>
        <div class="stat-num tabular-nums">{{ t.total }}</div>
        <div class="stat-meta">启用 {{ t.enabled }}</div>
      </article>
    </section>

    <!-- ========== Toolbar ========== -->
    <section class="toolbar">
      <div class="seg">
        <button class="seg-item" :class="{ active: !filters.channelType }" @click="setType('')">
          全部 <em class="tabular-nums">{{ stats?.total ?? 0 }}</em>
        </button>
        <button
          v-for="t in CHANNEL_TYPES"
          :key="t.value"
          class="seg-item"
          :class="{ active: filters.channelType === t.value }"
          @click="setType(t.value)"
        >
          <component :is="t.icon" :size="12" :stroke-width="1.7" />
          {{ t.short }}
          <em class="tabular-nums">{{ countOf(t.value) }}</em>
        </button>
      </div>

      <div class="filter-row">
        <el-select v-model="filters.status" placeholder="状态" clearable class="filter-select"
          @change="loadList">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索名称 / 编码 / 服务商"
          clearable
          class="filter-input"
          @keyup.enter="loadList"
          @clear="loadList"
        >
          <template #prefix><SearchIcon :size="13" :stroke-width="1.7" /></template>
        </el-input>
      </div>
    </section>

    <!-- ========== Channel Grid ========== -->
    <section v-loading="loading && list.length > 0" class="channel-grid"
      :style="{ minHeight: list.length ? 'auto' : '240px' }">
      <SkeletonList v-if="loading && list.length === 0" :rows="4" variant="row" />
      <article
        v-for="item in list"
        :key="item.id"
        class="channel-card"
        :class="{ disabled: item.status === 'DISABLED' }"
      >
        <span class="type-strip" :style="{ background: getChannelTypeMeta(item.channelType).color }" />

        <header class="card-head">
          <div class="channel-icon" :style="{
            color: getChannelTypeMeta(item.channelType).color,
            background: hexToRgba(getChannelTypeMeta(item.channelType).color, 0.1),
            boxShadow: `0 0 0 1px ${hexToRgba(getChannelTypeMeta(item.channelType).color, 0.25)}, 0 0 28px -6px ${hexToRgba(getChannelTypeMeta(item.channelType).color, 0.5)}`
          }">
            <component :is="getChannelTypeMeta(item.channelType).icon" :size="18" :stroke-width="1.6" />
          </div>
          <div class="head-meta">
            <div class="channel-name" :title="item.channelName">{{ item.channelName }}</div>
            <div class="sub-row">
              <span class="channel-code">{{ item.channelCode }}</span>
              <span class="sep">·</span>
              <span class="type-label">{{ item.channelTypeName }}</span>
            </div>
          </div>
          <span :class="['st-pill', item.status === 'ENABLED' ? 'on' : 'off']">
            <span class="st-dot" />{{ item.status === 'ENABLED' ? '启用' : '停用' }}
          </span>
        </header>

        <div class="info-row">
          <span class="lbl">服务商</span>
          <span class="val">{{ item.providerName || '—' }}</span>
        </div>
        <div class="info-row">
          <span class="lbl">优先级</span>
          <span class="val tabular-nums">{{ item.priority ?? 100 }}</span>
        </div>

        <div class="last-send" :class="lastClass(item.lastSendStatus)">
          <span class="ls-mark">▸</span>
          <span class="ls-label">LAST</span>
          <span class="ls-text">{{ lastText(item) }}</span>
        </div>

        <p v-if="item.description" class="desc" :title="item.description">{{ item.description }}</p>

        <footer class="card-actions">
          <button class="act-btn primary" @click="openTest(item)">
            <ZapIcon :size="13" :stroke-width="1.7" />测试
          </button>
          <button class="act-btn" @click="openEdit(item)">
            <EditIcon :size="13" :stroke-width="1.7" />编辑
          </button>
          <button class="act-btn" @click="onToggle(item)">
            <PowerIcon :size="13" :stroke-width="1.7" />{{ item.status === 'ENABLED' ? '停用' : '启用' }}
          </button>
          <el-popconfirm title="确认删除该渠道？" confirm-button-text="删除" cancel-button-text="取消"
            @confirm="onDelete(item)">
            <template #reference>
              <button class="act-btn danger">
                <TrashIcon :size="13" :stroke-width="1.7" />删除
              </button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <div v-if="!loading && list.length === 0" class="empty">
        <InboxIcon :size="28" :stroke-width="1.4" />
        <div class="empty-title">暂无通知渠道</div>
        <div class="empty-hint">先添加一个企业微信或邮件渠道，规则触发后才能发出告警。</div>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.7" /> 新增渠道
        </button>
      </div>
    </section>

    <!-- ========== 编辑对话框 ========== -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑通知渠道' : '新增通知渠道'"
      width="680px"
      :close-on-click-modal="false"
      class="channel-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="section-title"><span class="num">01</span>基础信息</div>
        <div class="form-grid">
          <el-form-item label="渠道名称" prop="channelName" class="span-3">
            <el-input v-model="form.channelName" placeholder="例如：DBA 值班企微群" />
          </el-form-item>
          <el-form-item label="渠道类型" prop="channelType">
            <el-select v-model="form.channelType" class="full" @change="onTypeChange">
              <el-option v-for="t in CHANNEL_TYPES" :key="t.value" :label="t.label" :value="t.value">
                <span class="opt-row">
                  <component :is="t.icon" :size="14" :style="{ color: t.color }" />
                  {{ t.label }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="渠道编码">
            <el-input v-model="form.channelCode" placeholder="留空自动生成" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="1" :max="999" controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="服务商">
            <el-input v-model="form.providerName" placeholder="选填，例如：腾讯企业邮 / 阿里云" />
          </el-form-item>
          <el-form-item label="状态" class="span-2">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <div class="section-title"><span class="num">02</span>渠道配置</div>
        <div class="config-hint">
          <Lightbulb :size="13" :stroke-width="1.7" />
          <span>{{ getChannelTypeMeta(form.channelType).configHint }}</span>
        </div>
        <el-input
          v-model="form.configJson"
          type="textarea"
          :rows="9"
          :placeholder="getChannelTypeMeta(form.channelType).configPlaceholder"
          class="config-json"
        />

        <div class="section-title"><span class="num">03</span>描述</div>
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit
          placeholder="选填" />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 测试对话框 ========== -->
    <el-dialog v-model="testDialogVisible" title="渠道测试" width="560px" class="channel-dialog">
      <div v-if="testTargetMeta" class="test-target">
        <div class="channel-icon" :style="{
          color: testTargetMeta.color,
          background: hexToRgba(testTargetMeta.color, 0.1),
          boxShadow: `0 0 0 1px ${hexToRgba(testTargetMeta.color, 0.25)}`
        }">
          <component :is="testTargetMeta.icon" :size="16" :stroke-width="1.6" />
        </div>
        <div class="test-target-meta">
          <div class="channel-name">{{ testTarget?.channelName }}</div>
          <div class="sub-row">
            <span class="type-label">{{ testTarget?.channelTypeName }}</span>
            <span class="sep">·</span>
            <span class="channel-code">{{ testTarget?.channelCode }}</span>
          </div>
        </div>
      </div>

      <el-form label-position="top" class="test-form">
        <el-form-item label="接收人" v-if="testTarget?.channelType !== 'WECOM'">
          <el-input v-model="testForm.receiverValue"
            :placeholder="testTarget?.channelType === 'EMAIL' ? '邮箱列表，多个用逗号分隔；留空使用渠道默认收件人' : '手机号列表'" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="testForm.title" placeholder="留空使用默认：AIOps Alert · 渠道测试" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="testForm.content" type="textarea" :rows="3" placeholder="留空使用默认测试内容" />
        </el-form-item>
      </el-form>

      <div v-if="lastTest" class="test-result" :class="lastTest.sendStatus === 'SUCCESS' ? 'ok' : 'fail'">
        <component :is="lastTest.sendStatus === 'SUCCESS' ? CheckIcon : AlertIcon" :size="14" :stroke-width="1.8" />
        <span class="r-text">
          {{ lastTest.sendStatus === 'SUCCESS' ? '测试发送成功' : '测试失败' }}
          <span v-if="lastTest.failureReason" class="reason">· {{ lastTest.failureReason }}</span>
          <span v-if="lastTest.providerMsgId" class="msgid">· msgId: {{ lastTest.providerMsgId }}</span>
        </span>
      </div>

      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="onTest">发送测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus as PlusIcon,
  RefreshCw as RefreshIcon,
  Search as SearchIcon,
  Edit3 as EditIcon,
  Power as PowerIcon,
  Trash2 as TrashIcon,
  Inbox as InboxIcon,
  Zap as ZapIcon,
  CheckCircle2 as CheckIcon,
  AlertTriangle as AlertIcon,
  Lightbulb
} from 'lucide-vue-next'
import { CHANNEL_TYPES, getChannelTypeMeta } from '@/utils/channelType'
import SkeletonList from '@/components/common/SkeletonList.vue'
import {
  deleteAlertChannel,
  getAlertChannelStats,
  listAlertChannels,
  saveAlertChannel,
  testAlertChannel,
  toggleAlertChannel,
  type AlertChannelItem,
  type AlertChannelStats,
  type AlertNotifyLogItem
} from '@/api/alertChannel'

const list = ref<AlertChannelItem[]>([])
const stats = ref<AlertChannelStats>()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const testDialogVisible = ref(false)
const testing = ref(false)
const formRef = ref<FormInstance>()
const lastTest = ref<AlertNotifyLogItem>()

const filters = reactive({ channelType: '', status: '', keyword: '' })

const emptyForm = (): AlertChannelItem => ({
  channelName: '',
  channelType: 'WECOM',
  channelCode: '',
  providerName: '',
  status: 'ENABLED',
  priority: 100,
  configJson: '',
  description: ''
})

const form = reactive<AlertChannelItem>(emptyForm())

const rules: FormRules = {
  channelName: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }],
  channelType: [{ required: true, message: '请选择渠道类型', trigger: 'change' }]
}

const testTarget = ref<AlertChannelItem>()
const testForm = reactive({ receiverValue: '', title: '', content: '' })
const testTargetMeta = computed(() =>
  testTarget.value ? getChannelTypeMeta(testTarget.value.channelType) : null
)

function countOf(type: string) {
  return stats.value?.byType.find((t) => t.channelType === type)?.total ?? 0
}

async function loadList() {
  loading.value = true
  try {
    list.value = await listAlertChannels({
      channelType: filters.channelType || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  stats.value = await getAlertChannelStats()
}

async function loadAll() {
  await Promise.all([loadList(), loadStats()])
}

function setType(type: string) {
  filters.channelType = type
  loadList()
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(item: AlertChannelItem) {
  Object.assign(form, emptyForm(), item)
  dialogVisible.value = true
}

function onTypeChange() {
  if (!form.configJson) {
    form.configJson = getChannelTypeMeta(form.channelType).configPlaceholder
  }
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.configJson?.trim()) {
    try {
      JSON.parse(form.configJson)
    } catch (e) {
      ElMessage.error('配置不是合法 JSON：' + (e as Error).message)
      return
    }
  }
  submitting.value = true
  try {
    await saveAlertChannel({ ...form })
    ElMessage.success(form.id ? '已更新' : '已创建')
    dialogVisible.value = false
    await loadAll()
  } finally {
    submitting.value = false
  }
}

async function onToggle(item: AlertChannelItem) {
  if (!item.id) return
  await toggleAlertChannel(item.id)
  ElMessage.success('已切换状态')
  await loadAll()
}

async function onDelete(item: AlertChannelItem) {
  if (!item.id) return
  await deleteAlertChannel(item.id)
  ElMessage.success('已删除')
  await loadAll()
}

function openTest(item: AlertChannelItem) {
  testTarget.value = item
  testForm.receiverValue = ''
  testForm.title = ''
  testForm.content = ''
  lastTest.value = undefined
  testDialogVisible.value = true
}

async function onTest() {
  if (!testTarget.value?.id) return
  testing.value = true
  try {
    lastTest.value = await testAlertChannel({
      channelId: testTarget.value.id,
      receiverValue: testForm.receiverValue || undefined,
      title: testForm.title || undefined,
      content: testForm.content || undefined
    })
    if (lastTest.value.sendStatus === 'SUCCESS') {
      ElMessage.success('测试已发送')
    } else {
      ElMessage.warning('测试未成功，请查看下方原因')
    }
    await loadList()
  } finally {
    testing.value = false
  }
}

function lastClass(status?: string) {
  if (status === 'SUCCESS') return 'ok'
  if (status === 'FAILED') return 'fail'
  return 'idle'
}

function lastText(item: AlertChannelItem) {
  if (!item.lastSendStatus) return '尚未发送'
  if (item.lastSendStatus === 'SUCCESS') return `成功 · ${item.lastSentAt || ''}`
  if (item.lastSendStatus === 'FAILED') return `失败 · ${item.lastFailureReason || '未知原因'}`
  return item.lastSendStatus
}

function hexToRgba(hex: string, alpha: number) {
  const m = hex.replace('#', '').match(/.{1,2}/g)
  if (!m) return hex
  const [r, g, b] = m.map((x) => parseInt(x, 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

onMounted(loadAll)
</script>


<style scoped>
.channels-v {
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

.hero-action.primary:hover { filter: brightness(1.08); }

/* ========== Stat bar ========== */
.stat-bar {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stat-cell {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  transition: all 0.15s ease;
}

.stat-cell.type {
  cursor: pointer;
}

.stat-cell.type:hover {
  border-color: var(--line-strong);
  transform: translateY(-2px);
}

.stat-cell.type.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.eyebrow.inline {
  margin: 0;
  font-size: 10px;
  letter-spacing: 0.18em;
}

.eyebrow.inline::before {
  display: none;
}

.stat-cell.ok .eyebrow { color: var(--ok); }
.stat-cell.danger .eyebrow { color: var(--danger); }

.stat-cell.ok :deep(svg) { color: var(--ok); }
.stat-cell.danger :deep(svg) { color: var(--danger); }

.type-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.stat-num {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 500;
  letter-spacing: -0.04em;
  line-height: 1;
  color: var(--text-primary);
}

.stat-cell.ok .stat-num { color: var(--ok); }
.stat-cell.danger .stat-num { color: var(--danger); }

.stat-meta {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

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
.filter-input { width: 260px; }

/* ========== Channel grid ========== */
.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
  position: relative;
}

.channel-card {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 18px 18px 14px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.channel-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
}

.channel-card.disabled { opacity: 0.55; }

.type-strip {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.channel-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.channel-card:hover .channel-icon {
  transform: scale(1.04);
}

.head-meta { flex: 1; min-width: 0; display: grid; gap: 3px; }

.channel-name {
  font-family: var(--font-display);
  font-size: 15.5px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.channel-code { color: var(--text-secondary); }
.sep { color: var(--text-faint); }
.type-label { color: var(--text-muted); }

.st-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.st-pill.on  { background: var(--ok-soft); color: var(--ok); }
.st-pill.off { background: var(--bg-elev-3); color: var(--text-muted); }

.st-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.st-pill.on .st-dot {
  animation: pulse-soft 2s ease-in-out infinite;
}

/* Info row */
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
}

.lbl {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.val {
  color: var(--text-primary);
  font-weight: 500;
}

/* Last send */
.last-send {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-left: 2px solid var(--text-faint);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  font-family: var(--font-mono);
  font-size: 11px;
}

.last-send.ok {
  border-left-color: var(--ok);
}

.last-send.fail {
  border-left-color: var(--danger);
  background: var(--danger-soft);
}

.last-send.idle { opacity: 0.7; }

.ls-mark {
  color: var(--text-faint);
  font-size: 11px;
  flex-shrink: 0;
}

.last-send.ok .ls-mark { color: var(--ok); }
.last-send.fail .ls-mark { color: var(--danger); }

.ls-label {
  color: var(--text-muted);
  letter-spacing: 0.18em;
  font-size: 9.5px;
}

.ls-text {
  flex: 1;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.last-send.ok .ls-text { color: var(--ok); }
.last-send.fail .ls-text { color: var(--danger); }

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

.act-btn.primary {
  background: var(--accent-soft);
  color: var(--accent);
}

.act-btn.primary:hover {
  background: var(--accent);
  color: var(--bg-base);
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

.empty-hint { font-size: 12px; margin-bottom: 8px; max-width: 360px; text-align: center; }

/* ========== Dialog ========== */
:deep(.channel-dialog .el-dialog) {
  background: var(--bg-elev-1);
  border: 1px solid var(--line);
  box-shadow: var(--inset);
}

:deep(.channel-dialog .el-dialog__title) {
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

.span-2 { grid-column: span 2; }
.span-3 { grid-column: span 3; }

.full { width: 100%; }

.opt-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.config-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 8px 12px;
  border: 1px solid var(--accent-line);
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
}

/* Config JSON textarea — 用 token，不写死颜色（修复 #0F172A bug） */
.config-json :deep(.el-textarea__inner) {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  background: var(--bg-elev-2);
  border-color: var(--line);
  color: var(--text-primary);
}

.config-json :deep(.el-textarea__inner:focus) {
  border-color: var(--accent);
}

/* Test target card */
.test-target {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
  margin-bottom: 14px;
}

.test-target .channel-icon {
  width: 36px;
  height: 36px;
}

.test-target-meta { display: grid; gap: 3px; }

.test-form :deep(.el-form-item) { margin-bottom: 14px; }

.test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  border: 1px solid;
}

.test-result.ok {
  background: var(--ok-soft);
  border-color: rgba(52, 211, 153, 0.35);
  color: var(--ok);
}

.test-result.fail {
  background: var(--danger-soft);
  border-color: rgba(248, 113, 113, 0.35);
  color: var(--danger);
}

.r-text { line-height: 1.6; }

.reason,
.msgid {
  color: var(--text-muted);
  margin-left: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
}

/* Responsive */
@media (max-width: 1380px) {
  .stat-bar { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 760px) {
  .channels-v { padding: 0 14px 24px; }
  .hero-num { font-size: 64px; }
  .stat-bar { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .form-grid { grid-template-columns: 1fr; }
  .span-2, .span-3 { grid-column: span 1; }
}
</style>
