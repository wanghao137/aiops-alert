<template>
  <div class="channels-view">
    <PageHeader
      eyebrow="ALERT CHANNELS"
      title="通知渠道管理"
      subtitle="配置企业微信、邮件、短信三类通知渠道，支持随时测试连通性。规则触发告警时按渠道分发，全部留痕。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增渠道</el-button>
      </template>
    </PageHeader>

    <!-- 顶部统计卡 -->
    <section class="stat-row">
      <StatCard label="渠道总数" :value="stats?.total ?? 0" :icon="InboxIcon" accent="#3B82F6"
        :hint="`其中 ${stats?.enabled ?? 0} 个启用`" />
      <StatCard label="今日发送成功" :value="stats?.sentToday ?? 0" :icon="CheckIcon" accent="#10B981"
        hint="所有通道累计" />
      <StatCard label="今日发送失败" :value="stats?.failedToday ?? 0" :icon="AlertIcon" accent="#EF4444"
        hint="可在事件详情重发" />
      <StatCard
        v-for="t in stats?.byType ?? []"
        :key="t.channelType"
        :label="t.channelTypeName"
        :value="t.total"
        :icon="getChannelTypeMeta(t.channelType).icon"
        :accent="getChannelTypeMeta(t.channelType).color"
        :hint="`启用 ${t.enabled}`"
      />
    </section>

    <!-- 筛选条 -->
    <section class="toolbar">
      <div class="type-tabs">
        <button class="type-tab" :class="{ active: !filters.channelType }" @click="setType('')">
          全部 <em>{{ stats?.total ?? 0 }}</em>
        </button>
        <button
          v-for="t in CHANNEL_TYPES"
          :key="t.value"
          class="type-tab"
          :class="{ active: filters.channelType === t.value }"
          :style="filters.channelType === t.value ? { borderColor: t.color, color: t.color } : {}"
          @click="setType(t.value)"
        >
          <component :is="t.icon" :size="14" />
          {{ t.short }}
          <em>{{ countOf(t.value) }}</em>
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
        class="channel-card"
        :style="{ background: getChannelTypeMeta(item.channelType).gradient }"
      >
        <header class="head">
          <div class="icon" :style="{ background: rgba(getChannelTypeMeta(item.channelType).color, 0.18), color: getChannelTypeMeta(item.channelType).color }">
            <component :is="getChannelTypeMeta(item.channelType).icon" :size="18" />
          </div>
          <div class="meta">
            <div class="name" :title="item.channelName">{{ item.channelName }}</div>
            <div class="code">{{ item.channelCode }}</div>
          </div>
          <span class="status-pill" :class="item.status === 'ENABLED' ? 'on' : 'off'">
            <span class="dot" />
            {{ item.status === 'ENABLED' ? '启用' : '停用' }}
          </span>
        </header>

        <div class="row">
          <span class="lbl">类型</span>
          <span>{{ item.channelTypeName }}</span>
        </div>
        <div class="row">
          <span class="lbl">服务商</span>
          <span>{{ item.providerName || '-' }}</span>
        </div>
        <div class="row">
          <span class="lbl">优先级</span>
          <span>{{ item.priority ?? 100 }}</span>
        </div>

        <div class="last-line">
          <span class="lbl">最近发送</span>
          <span class="last-status" :class="lastClass(item.lastSendStatus)">
            <span class="dot small" />
            {{ lastText(item) }}
          </span>
        </div>
        <p v-if="item.description" class="desc" :title="item.description">{{ item.description }}</p>

        <footer class="actions">
          <el-button text @click="openTest(item)">
            <ZapIcon :size="14" />&nbsp;测试
          </el-button>
          <el-button text @click="openEdit(item)">
            <EditIcon :size="14" />&nbsp;编辑
          </el-button>
          <el-button text @click="onToggle(item)">
            <PowerIcon :size="14" />&nbsp;{{ item.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
          <el-popconfirm title="确认删除该渠道？" confirm-button-text="删除" cancel-button-text="取消"
            @confirm="onDelete(item)">
            <template #reference>
              <el-button text type="danger">
                <TrashIcon :size="14" />&nbsp;删除
              </el-button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <div v-if="!loading && list.length === 0" class="empty">
        <div class="empty-icon"><InboxIcon :size="36" /></div>
        <div class="empty-title">暂无通知渠道</div>
        <div class="empty-hint">先添加一个企业微信或邮件渠道，规则触发后才能发出告警。</div>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增渠道</el-button>
      </div>
    </section>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑通知渠道' : '新增通知渠道'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="渠道名称" prop="channelName" class="span-2">
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
          <el-form-item label="服务商">
            <el-input v-model="form.providerName" placeholder="选填，例如：腾讯企业邮 / 阿里云" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="1" :max="999" controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="状态" class="span-2">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="渠道配置 (JSON)" class="span-2">
            <div class="config-hint">
              <Lightbulb :size="14" />
              <span>{{ getChannelTypeMeta(form.channelType).configHint }}</span>
            </div>
            <el-input
              v-model="form.configJson"
              type="textarea"
              :rows="9"
              :placeholder="getChannelTypeMeta(form.channelType).configPlaceholder"
              class="config-json"
            />
          </el-form-item>

          <el-form-item label="描述" class="span-2">
            <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="渠道测试" width="560px">
      <div v-if="testTargetMeta" class="test-target">
        <div class="icon" :style="{ background: rgba(testTargetMeta.color, 0.18), color: testTargetMeta.color }">
          <component :is="testTargetMeta.icon" :size="16" />
        </div>
        <div>
          <div class="name">{{ testTarget?.channelName }}</div>
          <div class="code">{{ testTarget?.channelTypeName }} · {{ testTarget?.channelCode }}</div>
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
        <component :is="lastTest.sendStatus === 'SUCCESS' ? CheckIcon : AlertIcon" :size="16" />
        <span>
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
import StatCard from '@/components/common/StatCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { CHANNEL_TYPES, getChannelTypeMeta } from '@/utils/channelType'
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
  // 切换类型后，如果配置为空，自动填充示例占位符为引导
  if (!form.configJson) {
    form.configJson = getChannelTypeMeta(form.channelType).configPlaceholder
  }
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 提前校验 JSON 合法
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

function rgba(hex: string, alpha: number) {
  const m = hex.replace('#', '').match(/.{1,2}/g)
  if (!m) return hex
  const [r, g, b] = m.map((x) => parseInt(x, 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

onMounted(loadAll)
</script>

<style scoped>
.channels-view {
  display: grid;
  gap: 16px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1380px) {
  .stat-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
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

.type-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

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

.type-tab:hover {
  color: var(--text-primary);
  border-color: var(--line-subtle);
}

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

.filter-row {
  display: flex;
  gap: 8px;
}

.filter-select {
  width: 130px;
}

.filter-input {
  width: 260px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.channel-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.channel-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-subtle);
  box-shadow: 0 16px 30px -20px rgba(0, 0, 0, 0.6);
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  flex-shrink: 0;
}

.meta {
  flex: 1;
  min-width: 0;
}

.name {
  color: var(--text-primary);
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code {
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  margin-top: 2px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  border: 1px solid transparent;
}

.status-pill.on {
  background: rgba(16, 185, 129, 0.12);
  color: #6EE7B7;
  border-color: rgba(16, 185, 129, 0.3);
}

.status-pill.off {
  background: rgba(148, 163, 184, 0.12);
  color: #94A3B8;
  border-color: rgba(148, 163, 184, 0.3);
}

.status-pill .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.lbl {
  color: var(--text-muted);
}

.last-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-top: 1px dashed var(--line);
  border-bottom: 1px dashed var(--line);
  font-size: 12px;
  color: var(--text-secondary);
}

.last-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.last-status.ok {
  color: #6EE7B7;
}

.last-status.fail {
  color: #FCA5A5;
}

.last-status.idle {
  color: var(--text-muted);
}

.dot.small {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
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

.empty-hint {
  color: var(--text-muted);
  margin-bottom: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 16px;
}

.span-2 {
  grid-column: span 3;
}

.full {
  width: 100%;
}

.opt-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.config-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #93C5FD;
  font-size: 12px;
}

.config-json :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
  background: #0F172A;
  border-color: var(--line);
}

.test-target {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-subtle);
  margin-bottom: 14px;
}

.test-target .icon {
  width: 32px;
  height: 32px;
}

.test-target .name {
  color: var(--text-primary);
  font-weight: 600;
}

.test-target .code {
  color: var(--text-muted);
  font-size: 12px;
}

.test-form .el-form-item {
  margin-bottom: 14px;
}

.test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}

.test-result.ok {
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: #6EE7B7;
}

.test-result.fail {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #FCA5A5;
}

.reason,
.msgid {
  color: var(--text-muted);
  margin-left: 4px;
}
</style>
