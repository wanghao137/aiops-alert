<template>
  <div class="events-view">
    <PageHeader
      eyebrow="ALERT EVENTS"
      title="告警事件中心"
      subtitle="所有告警事件的统一视图。AI 自动生成摘要与建议动作。SSE 实时推送，新事件从右上角弹出。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadAll">刷新</el-button>
        <el-dropdown trigger="click" @command="onTriggerCmd">
          <el-button type="primary">
            <ZapIcon :size="14" />&nbsp;触发演示
            <ChevronDown :size="13" style="margin-left: 4px;" />
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="story">
                <Sparkles :size="13" />&nbsp;启动故事模式（自动爬升）
              </el-dropdown-item>
              <el-dropdown-item command="manual" divided>
                <ZapIcon :size="13" />&nbsp;立即生成测试事件
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </PageHeader>

    <!-- 统计 -->
    <section class="stat-row">
      <StatCard label="事件总数" :value="counts.total" :icon="LayersIcon" accent="#3B82F6"
        :hint="`实时连接 ${realtime.recentEvents.length} 条`" />
      <StatCard label="待处理" :value="counts.pending" :icon="BellIcon" accent="#F59E0B"
        hint="需要优先确认" />
      <StatCard label="紧急 / 严重" :value="counts.high" :icon="AlertIcon" accent="#EF4444"
        :hint="`紧急 ${counts.critical}，严重 ${counts.serious}`" />
      <StatCard label="已恢复" :value="counts.recovered" :icon="CheckIcon" accent="#10B981"
        hint="处理流程已闭环" />
    </section>

    <div class="layout">
      <!-- 状态侧栏 -->
      <aside class="side">
        <div class="side-title">
          <strong>事件队列</strong>
          <span>{{ allEvents.length }} 条</span>
        </div>
        <button
          v-for="t in statusTabs"
          :key="t.value || 'ALL'"
          class="side-item"
          :class="{ active: filters.eventStatus === t.value }"
          @click="setStatus(t.value)"
        >
          <span class="side-text">
            <b>{{ t.label }}</b>
            <small>{{ t.hint }}</small>
          </span>
          <em>{{ t.count }}</em>
        </button>

        <!-- 实时流 -->
        <div class="live-block" v-if="realtime.recentEvents.length">
          <div class="live-title">
            <Radio :size="13" /> 实时事件
          </div>
          <div class="live-item" v-for="ev in realtime.recentEvents.slice(0, 5)" :key="ev.id">
            <span class="live-dot" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
            <div class="live-meta">
              <div class="live-name">{{ ev.objectName }}</div>
              <div class="live-sub">{{ ev.metricName }} · {{ ev.currentValue || '' }}</div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 事件列表 -->
      <section class="main">
        <div class="filters">
          <el-select v-model="filters.objectType" placeholder="对象类型" clearable class="filter-select"
            @change="loadAll">
            <el-option v-for="t in OBJECT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="filters.alertLevel" placeholder="级别" clearable class="filter-select"
            @change="loadAll">
            <el-option v-for="l in ALERT_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
          <el-input v-model="filters.keyword" placeholder="搜索标题 / 对象 / 编号" clearable
            class="filter-input" @keyup.enter="loadAll" @clear="loadAll">
            <template #prefix><SearchIcon :size="14" /></template>
          </el-input>
        </div>

        <div v-loading="loading" class="event-list">
          <article
            v-for="ev in events"
            :key="ev.id"
            class="event-card"
            :class="{ active: detail?.id === ev.id }"
            @click="openDetail(ev)"
          >
            <div class="lv-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
            <div class="ev-body">
              <div class="ev-row1">
                <span class="ev-title" :title="ev.eventTitle">{{ ev.eventTitle }}</span>
                <span class="lv-pill" :style="{
                  background: getAlertLevelMeta(ev.alertLevel).bg,
                  color: getAlertLevelMeta(ev.alertLevel).color
                }">
                  <component :is="getAlertLevelMeta(ev.alertLevel).icon" :size="11" />
                  {{ getAlertLevelMeta(ev.alertLevel).label }}
                </span>
              </div>
              <div class="ev-row2">
                <span class="ev-no">{{ ev.eventNo }}</span>
                <span class="dot-sep" />
                <span class="ev-obj">
                  <component :is="getObjectTypeMeta(ev.objectType).icon" :size="11"
                    :style="{ color: getObjectTypeMeta(ev.objectType).color }" />
                  {{ ev.objectName }}
                </span>
                <span class="dot-sep" />
                <span :class="['st-pill', evStatusClass(ev.eventStatus)]">{{ evStatusName(ev.eventStatus) }}</span>
                <span v-if="ev.aiSummaryStatus === 'SUCCESS'" class="ai-pill" title="AI 摘要已生成">
                  <Sparkles :size="10" /> AI
                </span>
                <span v-else-if="ev.aiSummaryStatus === 'PENDING'" class="ai-pill pending">
                  <Sparkles :size="10" /> 思考中
                </span>
              </div>
              <div class="ev-row3">
                <span class="metric">{{ ev.metricName }}</span>
                <code class="value" v-if="ev.currentValue">{{ ev.currentValue }}</code>
                <span class="dot-sep" />
                <span class="time">{{ formatTime(ev.lastTriggeredAt) }}</span>
              </div>
            </div>
          </article>

          <div v-if="!loading && !events.length" class="empty">
            <BellOff :size="36" />
            <div class="empty-title">暂无告警事件</div>
            <div class="empty-hint">点击"手动触发"造一条用于演示，或等模拟器命中规则。</div>
          </div>
        </div>
      </section>

      <!-- 详情 -->
      <aside class="detail" v-if="detail">
        <button class="close-btn" @click="detail = undefined">
          <X :size="16" />
        </button>

        <div class="detail-head">
          <div class="lv-bar" :style="{ background: getAlertLevelMeta(detail.alertLevel).color }" />
          <div>
            <div class="title">{{ detail.eventTitle }}</div>
            <div class="sub">
              <span>{{ detail.eventNo }}</span>
              <span class="dot-sep" />
              <span>{{ getAlertLevelMeta(detail.alertLevel).label }}</span>
              <span class="dot-sep" />
              <span :class="['st-pill', evStatusClass(detail.eventStatus)]">
                {{ evStatusName(detail.eventStatus) }}
              </span>
            </div>
          </div>
        </div>

        <!-- AI 摘要 -->
        <AiSummaryCard
          :raw-summary="detail.aiSummary"
          :raw-status="detail.aiSummaryStatus"
          :loading="summaryLoading"
          @refresh="onRefreshSummary"
        />

        <!-- AI 思考过程（推理类模型独有） -->
        <ThinkingPanel
          v-if="detail.aiReasoning"
          :content="detail.aiReasoning"
          title="AI 思考过程"
        />

        <!-- 元数据 -->
        <div class="kv">
          <div><span class="k">监控对象</span><span class="v">{{ detail.objectName }}</span></div>
          <div><span class="k">指标</span><span class="v">{{ detail.metricName }}</span></div>
          <div>
            <span class="k">当前值 / 阈值</span>
            <span class="v">
              <code>{{ detail.currentValue || '-' }}</code>
              <span class="muted">/ {{ detail.thresholdValue || '-' }}</span>
            </span>
          </div>
          <div><span class="k">规则</span><span class="v">{{ detail.ruleName || '-' }}</span></div>
          <div><span class="k">首次触发</span><span class="v">{{ formatTime(detail.firstTriggeredAt) }}</span></div>
          <div><span class="k">最近触发</span><span class="v">{{ formatTime(detail.lastTriggeredAt) }}</span></div>
        </div>

        <!-- 操作 -->
        <div class="actions">
          <el-button type="primary" :disabled="detail.eventStatus === 'CONFIRMED' || detail.eventStatus === 'CLOSED'"
            @click="onAction('CONFIRM')">
            <CheckCircle2 :size="14" /> &nbsp;确认
          </el-button>
          <el-button :disabled="detail.eventStatus === 'RECOVERED' || detail.eventStatus === 'CLOSED'"
            @click="onAction('RECOVER')">
            <Activity :size="14" /> &nbsp;恢复
          </el-button>
          <el-button type="danger" :disabled="detail.eventStatus === 'CLOSED'"
            @click="onAction('CLOSE')">
            <X :size="14" /> &nbsp;关闭
          </el-button>
        </div>

        <!-- 通知日志 -->
        <div v-if="detail.notifyLogs?.length" class="logs">
          <div class="logs-title">通知发送 ({{ detail.notifyLogs.length }})</div>
          <div v-for="n in detail.notifyLogs" :key="n.id" class="log-item">
            <span class="log-icon" :style="{ color: getChannelTypeMeta(n.channelType).color }">
              <component :is="getChannelTypeMeta(n.channelType).icon" :size="13" />
            </span>
            <div class="log-meta">
              <div class="log-line">
                <strong>{{ getChannelTypeMeta(n.channelType).label }}</strong>
                <span class="muted">{{ n.receiverValue || '-' }}</span>
              </div>
              <div class="log-line2">
                <span :class="['notify-pill', notifyStatusClass(n.sendStatus)]">{{ notifyStatusName(n.sendStatus) }}</span>
                <span class="muted" v-if="n.sentAt">{{ formatTime(n.sentAt) }}</span>
                <span class="muted err" v-if="n.failureReason">· {{ n.failureReason }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 处理记录 -->
        <div v-if="detail.handleLogs?.length" class="logs">
          <div class="logs-title">处理记录 ({{ detail.handleLogs.length }})</div>
          <div v-for="h in detail.handleLogs" :key="h.id" class="log-item">
            <span class="log-icon"><Clock :size="13" /></span>
            <div class="log-meta">
              <div class="log-line">
                <strong>{{ h.actionTypeName || h.actionType }}</strong>
                <span class="muted">{{ h.operatorName || '-' }}</span>
              </div>
              <div class="log-line2">
                <span class="muted">{{ formatTime(h.createdAt) }}</span>
                <span class="muted" v-if="h.beforeStatus && h.afterStatus">{{ h.beforeStatus }} → {{ h.afterStatus }}</span>
                <span class="muted" v-if="h.actionComment">· {{ h.actionComment }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 手动触发 -->
    <el-dialog v-model="triggerVisible" title="手动触发告警事件" width="520px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="选择规则">
          <el-select v-model="triggerForm.ruleId" filterable class="full" placeholder="选择已启用的规则"
            @change="onTriggerRuleChange">
            <el-option v-for="r in ruleOptions" :key="r.id" :label="r.ruleName" :value="r.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择对象">
          <el-select v-model="triggerForm.objectId" filterable class="full"
            placeholder="该规则绑定的对象">
            <el-option v-for="o in triggerObjects" :key="o.id" :label="o.objectName" :value="o.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前值（可选）">
          <el-input v-model="triggerForm.currentValue" placeholder="例如 cpu_usage=92%" />
        </el-form-item>
        <el-form-item label="触发原因（可选）">
          <el-input v-model="triggerForm.eventReason" type="textarea" :rows="2"
            placeholder="留空则使用默认描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triggerVisible = false">取消</el-button>
        <el-button type="primary" :loading="triggering" @click="onTriggerSubmit">触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import {
  RefreshCw as RefreshIcon,
  Zap as ZapIcon,
  Search as SearchIcon,
  Layers as LayersIcon,
  Bell as BellIcon,
  AlertTriangle as AlertIcon,
  CheckCircle2 as CheckIcon,
  Sparkles, Activity, Clock,
  Radio, BellOff, X, CheckCircle2,
  ChevronDown
} from 'lucide-vue-next'
import StatCard from '@/components/common/StatCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import AiSummaryCard from '@/components/alert/AiSummaryCard.vue'
import ThinkingPanel from '@/components/ai/ThinkingPanel.vue'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import { getChannelTypeMeta } from '@/utils/channelType'
import { ALERT_LEVELS, getAlertLevelMeta } from '@/utils/alertLevel'
import {
  createTestAlertEvent,
  getAlertEvent,
  handleAlertEvent,
  listAlertEvents,
  summarizeAlertEvent,
  type AlertEventItem
} from '@/api/alertEvent'
import { listAlertRules, type AlertRuleItem } from '@/api/alertRule'
import { listMonitorObjects, type MonitorObjectItem } from '@/api/monitorObject'
import { useRealtimeStore } from '@/stores/realtime'
import { forceStory } from '@/api/simulator'

const allEvents = ref<AlertEventItem[]>([])
const detail = ref<AlertEventItem>()
const summaryLoading = ref(false)
const loading = ref(false)
const filters = reactive({ objectType: '', alertLevel: '', eventStatus: '', keyword: '' })
const realtime = useRealtimeStore()

const events = computed(() => {
  if (!filters.eventStatus) return allEvents.value
  return allEvents.value.filter((e) => e.eventStatus === filters.eventStatus)
})

const counts = computed(() => {
  const total = allEvents.value.length
  const pending = allEvents.value.filter((e) => e.eventStatus === 'PENDING').length
  const recovered = allEvents.value.filter((e) => e.eventStatus === 'RECOVERED').length
  const critical = allEvents.value.filter((e) => e.alertLevel === 'CRITICAL').length
  const serious = allEvents.value.filter((e) => e.alertLevel === 'SERIOUS').length
  return { total, pending, recovered, critical, serious, high: critical + serious }
})

const statusTabs = computed(() => [
  { value: '', label: '全部', hint: '所有事件', count: allEvents.value.length },
  { value: 'PENDING', label: '待处理', hint: '需要优先确认', count: counts.value.pending },
  { value: 'CONFIRMED', label: '已确认', hint: '处理中', count: allEvents.value.filter((e) => e.eventStatus === 'CONFIRMED').length },
  { value: 'RECOVERED', label: '已恢复', hint: '异常已恢复', count: counts.value.recovered },
  { value: 'CLOSED', label: '已关闭', hint: '处理完成', count: allEvents.value.filter((e) => e.eventStatus === 'CLOSED').length }
])

async function loadAll() {
  loading.value = true
  try {
    allEvents.value = await listAlertEvents({
      objectType: filters.objectType || undefined,
      alertLevel: filters.alertLevel || undefined,
      keyword: filters.keyword || undefined
    })
  } finally {
    loading.value = false
  }
}

function setStatus(v: string) {
  filters.eventStatus = v
}

async function openDetail(ev: AlertEventItem) {
  detail.value = await getAlertEvent(ev.id)
}

async function onAction(action: 'CONFIRM' | 'RECOVER' | 'CLOSE') {
  if (!detail.value) return
  await handleAlertEvent({
    eventId: detail.value.id,
    actionType: action,
    operatorName: 'admin'
  })
  ElMessage.success('操作已记录')
  await Promise.all([loadAll(), openDetail(detail.value)])
}

async function onRefreshSummary() {
  if (!detail.value) return
  summaryLoading.value = true
  try {
    await summarizeAlertEvent(detail.value.id)
    // 后端是异步的，等 1.5 秒再拉详情看摘要状态
    setTimeout(async () => {
      if (detail.value) {
        detail.value = await getAlertEvent(detail.value.id)
      }
      summaryLoading.value = false
    }, 1500)
  } catch {
    summaryLoading.value = false
  }
}

// 实时事件来时刷新列表
watch(() => realtime.lastEventId, () => loadAll())
watch(() => realtime.aiSummaryUpdates, async () => {
  if (detail.value) {
    detail.value = await getAlertEvent(detail.value.id)
  }
})

// ----------- 手动触发 -----------
const triggerVisible = ref(false)
const triggering = ref(false)
const triggerForm = reactive({
  ruleId: undefined as number | undefined,
  objectId: undefined as number | undefined,
  currentValue: '',
  eventReason: ''
})
const ruleOptions = ref<AlertRuleItem[]>([])
const triggerObjects = ref<MonitorObjectItem[]>([])

async function onTriggerCmd(cmd: string | number | object) {
  if (cmd === 'manual') {
    await openTrigger()
    return
  }
  if (cmd === 'story') {
    await onStartStory()
    return
  }
}

/** 启动故事模式：随机挑一个启用的规则 + 对象 + 数值类指标，让模拟器爬升 */
async function onStartStory() {
  const rules = await listAlertRules({ status: 'ENABLED' })
  if (!rules.length) {
    ElMessage.warning('没有启用的告警规则，请先创建规则')
    return
  }
  // 优先挑数值条件的规则（更利于"爬升"演示）
  const numericRules = rules.filter((r) =>
    (r.conditions || []).some((c) => ['GT', 'GE', 'LT', 'LE'].includes(c.compareOp))
  )
  const pool = numericRules.length ? numericRules : rules
  const rule = pool[Math.floor(Math.random() * pool.length)]
  const objectIds = rule.objectIds || []
  if (!objectIds.length) {
    ElMessage.warning(`规则 "${rule.ruleName}" 没有绑定对象`)
    return
  }
  const objectId = objectIds[Math.floor(Math.random() * objectIds.length)]
  const cond = (rule.conditions || [])[0]
  if (!cond?.metricCode) {
    ElMessage.warning(`规则 "${rule.ruleName}" 没有有效的触发条件`)
    return
  }
  const objs = await listMonitorObjects({ objectType: rule.objectType, status: 'ENABLED' })
  const objName = objs.find((o) => o.id === objectId)?.objectName || `#${objectId}`
  const tip = await forceStory(objectId, cond.metricCode)
  ElMessage.success(`故事已启动：${objName} · ${cond.metricName}。${tip}`)
}

async function openTrigger() {
  triggerForm.ruleId = undefined
  triggerForm.objectId = undefined
  triggerForm.currentValue = ''
  triggerForm.eventReason = ''
  ruleOptions.value = await listAlertRules({ status: 'ENABLED' })
  triggerVisible.value = true
}

async function onTriggerRuleChange() {
  triggerForm.objectId = undefined
  const rule = ruleOptions.value.find((r) => r.id === triggerForm.ruleId)
  if (!rule) {
    triggerObjects.value = []
    return
  }
  const allObjs = await listMonitorObjects({ objectType: rule.objectType, status: 'ENABLED' })
  const allowed = new Set(rule.objectIds || [])
  triggerObjects.value = allObjs.filter((o) => o.id && allowed.has(o.id))
}

async function onTriggerSubmit() {
  if (!triggerForm.ruleId || !triggerForm.objectId) {
    ElMessage.warning('请选择规则和对象')
    return
  }
  triggering.value = true
  try {
    await createTestAlertEvent({
      ruleId: triggerForm.ruleId,
      objectId: triggerForm.objectId,
      currentValue: triggerForm.currentValue || undefined,
      eventReason: triggerForm.eventReason || undefined
    })
    ElMessage.success('已触发，请稍后查看 AI 摘要')
    triggerVisible.value = false
    await loadAll()
  } finally {
    triggering.value = false
  }
}

// ----------- helpers -----------
function evStatusName(s?: string) {
  const m: Record<string, string> = {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    RECOVERED: '已恢复',
    CLOSED: '已关闭'
  }
  return m[s || ''] || s || '-'
}

function evStatusClass(s?: string) {
  if (s === 'PENDING') return 'pending'
  if (s === 'CONFIRMED') return 'confirmed'
  if (s === 'RECOVERED') return 'recovered'
  if (s === 'CLOSED') return 'closed'
  return ''
}

function notifyStatusName(s?: string) {
  const m: Record<string, string> = {
    PENDING: '发送中',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return m[s || ''] || s || '-'
}

function notifyStatusClass(s?: string) {
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED') return 'fail'
  return 'pending'
}

function formatTime(t?: string) {
  return t ? dayjs(t).format('MM-DD HH:mm:ss') : '-'
}

onMounted(loadAll)
</script>

<style scoped>
.events-view { display: grid; gap: 16px; }

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1280px) { .stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

.layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1.2fr) minmax(0, 1.4fr);
  gap: 14px;
}

@media (max-width: 1280px) {
  .layout { grid-template-columns: 240px minmax(0, 1fr); }
  .detail { grid-column: 1 / -1; }
}

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}

.side {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
}

.side-title {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 4px;
}

.side-title strong { color: var(--text-primary); font-size: 14px; }
.side-title span { color: var(--text-muted); font-size: 12px; }

.side-item {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.side-item:hover {
  background: var(--bg-subtle);
  border-color: var(--line);
}

.side-item.active {
  background: rgba(59, 130, 246, 0.08);
  border-color: rgba(59, 130, 246, 0.4);
}

.side-text b {
  display: block;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
}

.side-text small {
  display: block;
  color: var(--text-muted);
  font-size: 11px;
  margin-top: 1px;
}

.side-item em {
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-style: normal;
  font-size: 11px;
  font-weight: 600;
}

.side-item.active em {
  background: var(--accent);
  color: white;
}

.live-block {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.live-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #6EE7B7;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.4px;
  margin-bottom: 6px;
}

.live-item {
  display: grid;
  grid-template-columns: 6px 1fr;
  gap: 8px;
  padding: 6px 0;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-top: 6px;
}

.live-name {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 500;
}

.live-sub {
  color: var(--text-muted);
  font-size: 11px;
  margin-top: 1px;
}

.main { display: grid; gap: 12px; }

.filters {
  display: flex;
  gap: 8px;
}

.filter-select { width: 130px; }
.filter-input  { width: 100%; }

.event-list {
  display: grid;
  gap: 8px;
  min-height: 200px;
}

.event-card {
  display: grid;
  grid-template-columns: 4px 1fr;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-panel);
  cursor: pointer;
  transition: all 0.15s ease;
}

.event-card:hover {
  transform: translateY(-1px);
  border-color: var(--line-subtle);
}

.event-card.active {
  border-color: var(--accent);
  background: rgba(59, 130, 246, 0.05);
}

.lv-bar {
  border-radius: 4px;
}

.ev-body { display: grid; gap: 4px; min-width: 0; }

.ev-row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ev-title {
  flex: 1;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lv-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.ev-row2,
.ev-row3 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 11px;
}

.ev-no { font-family: 'JetBrains Mono', monospace; }

.dot-sep {
  width: 3px; height: 3px;
  background: var(--text-muted);
  border-radius: 50%;
  opacity: 0.5;
}

.ev-obj {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.st-pill {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.st-pill.pending   { background: rgba(245, 158, 11, 0.15); color: #FCD34D; }
.st-pill.confirmed { background: rgba(59, 130, 246, 0.15); color: #93C5FD; }
.st-pill.recovered { background: rgba(16, 185, 129, 0.15); color: #6EE7B7; }
.st-pill.closed    { background: rgba(148, 163, 184, 0.15); color: #94A3B8; }

.ai-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 7px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(59,130,246,0.18), rgba(139,92,246,0.18));
  color: #C4B5FD;
  font-size: 10px;
  font-weight: 600;
  border: 1px solid rgba(139,92,246,0.3);
}

.ai-pill.pending {
  color: #FCD34D;
  background: rgba(245,158,11,0.15);
  border-color: rgba(245,158,11,0.3);
}

.metric { color: var(--text-secondary); }

.value {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-subtle);
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.empty {
  display: grid;
  place-items: center;
  gap: 6px;
  padding: 60px 20px;
  border: 1px dashed var(--line-subtle);
  border-radius: 10px;
  color: var(--text-muted);
}

.empty-title { color: var(--text-primary); font-size: 14px; font-weight: 600; }
.empty-hint  { font-size: 12px; }

.detail {
  position: sticky;
  top: 76px;
  max-height: calc(100vh - 100px);
  overflow: auto;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
  display: grid;
  gap: 14px;
  align-content: start;
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.close-btn:hover {
  border-color: var(--line-subtle);
  color: var(--text-primary);
}

.detail-head {
  display: grid;
  grid-template-columns: 4px 1fr;
  gap: 12px;
  padding-right: 28px;
}

.detail-head .lv-bar { border-radius: 4px; height: 100%; }
.detail-head .title { color: var(--text-primary); font-size: 15px; font-weight: 600; }
.detail-head .sub {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 4px;
}

.kv {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-subtle);
}

.kv > div {
  display: grid;
  gap: 2px;
}

.kv .k { color: var(--text-muted); font-size: 11px; }
.kv .v { color: var(--text-primary); font-size: 13px; }
.kv .v code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-panel);
  font-family: 'JetBrains Mono', monospace;
}
.kv .v .muted { color: var(--text-muted); margin-left: 4px; }

.actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.logs { display: grid; gap: 6px; }

.logs-title {
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0.4px;
}

.log-item {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-subtle);
}

.log-icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
}

.log-line { display: flex; gap: 8px; align-items: baseline; }
.log-line strong { color: var(--text-primary); font-size: 12px; }
.log-line .muted { color: var(--text-muted); font-size: 11px; }

.log-line2 { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 2px; font-size: 11px; }
.log-line2 .muted { color: var(--text-muted); }
.log-line2 .err { color: #FCA5A5; }

.notify-pill {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.notify-pill.ok { background: rgba(16,185,129,0.15); color: #6EE7B7; }
.notify-pill.fail { background: rgba(239,68,68,0.15); color: #FCA5A5; }
.notify-pill.pending { background: rgba(245,158,11,0.15); color: #FCD34D; }

.full { width: 100%; }
</style>
