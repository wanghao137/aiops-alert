<template>
  <div class="events-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">EVENT CENTER</span>
          <span class="dot-anim" />
          <span class="hero-time">{{ realtime.recentEvents.length }} 条实时</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num" :class="{ urgent: counts.pending > 0 }">{{ counts.pending }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ counts.pending > 0 ? '待处理告警' : '一切平静' }}</div>
            <div class="hero-line-2">
              累计 {{ counts.total }} · 紧急 {{ counts.critical }} · 严重 {{ counts.serious }} · 已恢复 {{ counts.recovered }}
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <button class="hero-action ghost" @click="loadAll">
          <RefreshIcon :size="13" :stroke-width="1.6" />
          刷新
        </button>
        <el-dropdown trigger="click" @command="onTriggerCmd">
          <button class="hero-action primary">
            <ZapIcon :size="13" :stroke-width="1.6" />
            触发演示
            <ChevronDown :size="12" :stroke-width="1.6" />
          </button>
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
      </div>
    </section>

    <!-- ========== 三栏：状态侧栏 / 事件列表 / 详情 ========== -->
    <section class="layout">
      <!-- 左：状态侧栏 + 实时流 -->
      <aside class="side">
        <div class="side-section">
          <div class="eyebrow">QUEUE</div>
          <div class="tabs">
            <button
              v-for="t in statusTabs"
              :key="t.value || 'ALL'"
              class="tab"
              :class="{ active: filters.eventStatus === t.value }"
              @click="setStatus(t.value)"
            >
              <span class="tab-name">{{ t.label }}</span>
              <span class="tab-hint">{{ t.hint }}</span>
              <span class="tab-count tabular-nums">{{ String(t.count).padStart(2, '0') }}</span>
            </button>
          </div>
        </div>

        <div v-if="realtime.recentEvents.length" class="side-section">
          <div class="eyebrow"><span class="live-dot" />LIVE FEED</div>
          <div class="live-list">
            <div
              v-for="ev in realtime.recentEvents.slice(0, 6)"
              :key="`live-${ev.id}`"
              class="live-row"
            >
              <span class="live-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
              <div class="live-meta">
                <div class="live-name">{{ ev.objectName }}</div>
                <div class="live-sub">{{ ev.metricName }}<span v-if="ev.currentValue"> · {{ ev.currentValue }}</span></div>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中：事件列表 -->
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
            <template #prefix><SearchIcon :size="13" :stroke-width="1.6" /></template>
          </el-input>
        </div>

        <div v-loading="loading && events.length > 0" class="event-list">
          <SkeletonList v-if="loading && events.length === 0" :rows="6" />
          <article
            v-for="ev in events"
            :key="ev.id"
            class="event-card"
            :class="{ active: detail?.id === ev.id }"
            @click="openDetail(ev)"
          >
            <span class="lv-bar" :style="{ background: getAlertLevelMeta(ev.alertLevel).color }" />
            <div class="ev-body">
              <div class="ev-row1">
                <span class="ev-no">{{ ev.eventNo }}</span>
                <span class="lv-tag" :style="{
                  color: getAlertLevelMeta(ev.alertLevel).color,
                  borderColor: getAlertLevelMeta(ev.alertLevel).color
                }">{{ getAlertLevelMeta(ev.alertLevel).label }}</span>
                <span :class="['st-pill', evStatusClass(ev.eventStatus)]">{{ evStatusName(ev.eventStatus) }}</span>
                <span v-if="ev.aiSummaryStatus === 'SUCCESS'" class="ai-pill" title="AI 摘要已生成">
                  <Sparkles :size="10" :stroke-width="1.8" /> AI
                </span>
                <span v-else-if="ev.aiSummaryStatus === 'PENDING'" class="ai-pill pending">
                  <Sparkles :size="10" :stroke-width="1.8" /> 思考中
                </span>
                <span class="time tabular-nums">{{ formatTime(ev.lastTriggeredAt) }}</span>
              </div>
              <div class="ev-title">{{ ev.eventTitle }}</div>
              <div class="ev-meta">
                <component :is="getObjectTypeMeta(ev.objectType).icon" :size="12" :stroke-width="1.6"
                  :style="{ color: getObjectTypeMeta(ev.objectType).color }" />
                <span class="obj">{{ ev.objectName }}</span>
                <span class="sep">·</span>
                <span class="metric">{{ ev.metricName }}</span>
                <code v-if="ev.currentValue">{{ ev.currentValue }}</code>
              </div>
            </div>
          </article>

          <div v-if="!loading && !events.length" class="empty">
            <BellOff :size="28" :stroke-width="1.4" />
            <div class="empty-title">暂无告警事件</div>
            <div class="empty-hint">点击"触发演示"造一条用于演示，或等模拟器命中规则。</div>
          </div>
        </div>
      </section>

      <!-- 右：详情 -->
      <aside class="detail" v-if="detail">
        <button class="close-btn" @click="detail = undefined">
          <X :size="14" :stroke-width="1.6" />
        </button>

        <div class="detail-head">
          <span class="lv-strip" :style="{ background: getAlertLevelMeta(detail.alertLevel).color }" />
          <div>
            <div class="eyebrow">EVENT · {{ detail.eventNo }}</div>
            <h3 class="detail-title">{{ detail.eventTitle }}</h3>
            <div class="detail-sub">
              <span class="lv-tag" :style="{
                color: getAlertLevelMeta(detail.alertLevel).color,
                borderColor: getAlertLevelMeta(detail.alertLevel).color
              }">{{ getAlertLevelMeta(detail.alertLevel).label }}</span>
              <span :class="['st-pill', evStatusClass(detail.eventStatus)]">{{ evStatusName(detail.eventStatus) }}</span>
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

        <!-- AI 思考 -->
        <ThinkingPanel
          v-if="detail.aiReasoning"
          :content="detail.aiReasoning"
          title="AI 思考过程"
        />

        <!-- 操作 -->
        <div class="actions-bar">
          <button class="op-btn primary"
            :disabled="detail.eventStatus === 'CONFIRMED' || detail.eventStatus === 'CLOSED'"
            @click="onAction('CONFIRM')">
            <CheckCircle2 :size="13" :stroke-width="1.6" /> 确认
          </button>
          <button class="op-btn"
            :disabled="detail.eventStatus === 'RECOVERED' || detail.eventStatus === 'CLOSED'"
            @click="onAction('RECOVER')">
            <Activity :size="13" :stroke-width="1.6" /> 恢复
          </button>
          <button class="op-btn danger"
            :disabled="detail.eventStatus === 'CLOSED'"
            @click="onAction('CLOSE')">
            <X :size="13" :stroke-width="1.6" /> 关闭
          </button>
        </div>

        <!-- 元数据 -->
        <div class="kv-grid">
          <div><span class="k">监控对象</span><span class="v">{{ detail.objectName }}</span></div>
          <div><span class="k">指标</span><span class="v">{{ detail.metricName }}</span></div>
          <div>
            <span class="k">当前值 · 阈值</span>
            <span class="v">
              <code>{{ detail.currentValue || '-' }}</code>
              <span class="muted">/ {{ detail.thresholdValue || '-' }}</span>
            </span>
          </div>
          <div><span class="k">规则</span><span class="v">{{ detail.ruleName || '-' }}</span></div>
          <div><span class="k">首次触发</span><span class="v tabular-nums">{{ formatTime(detail.firstTriggeredAt) }}</span></div>
          <div><span class="k">最近触发</span><span class="v tabular-nums">{{ formatTime(detail.lastTriggeredAt) }}</span></div>
        </div>

        <!-- 通知日志 -->
        <div v-if="detail.notifyLogs?.length" class="logs">
          <div class="eyebrow">NOTIFICATIONS · {{ detail.notifyLogs.length }}</div>
          <div v-for="n in detail.notifyLogs" :key="n.id" class="log-item">
            <span class="log-icon" :style="{ color: getChannelTypeMeta(n.channelType).color }">
              <component :is="getChannelTypeMeta(n.channelType).icon" :size="13" :stroke-width="1.6" />
            </span>
            <div class="log-meta">
              <div class="log-line">
                <strong>{{ getChannelTypeMeta(n.channelType).label }}</strong>
                <span class="muted">{{ n.receiverValue || '-' }}</span>
              </div>
              <div class="log-line2">
                <span :class="['notify-pill', notifyStatusClass(n.sendStatus)]">{{ notifyStatusName(n.sendStatus) }}</span>
                <span class="muted tabular-nums" v-if="n.sentAt">{{ formatTime(n.sentAt) }}</span>
                <span class="muted err" v-if="n.failureReason">· {{ n.failureReason }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 处理记录 -->
        <div v-if="detail.handleLogs?.length" class="logs">
          <div class="eyebrow">HISTORY · {{ detail.handleLogs.length }}</div>
          <div v-for="h in detail.handleLogs" :key="h.id" class="log-item">
            <span class="log-icon"><Clock :size="13" :stroke-width="1.6" /></span>
            <div class="log-meta">
              <div class="log-line">
                <strong>{{ h.actionTypeName || h.actionType }}</strong>
                <span class="muted">{{ h.operatorName || '-' }}</span>
              </div>
              <div class="log-line2">
                <span class="muted tabular-nums">{{ formatTime(h.createdAt) }}</span>
                <span class="muted" v-if="h.beforeStatus && h.afterStatus">{{ h.beforeStatus }} → {{ h.afterStatus }}</span>
                <span class="muted" v-if="h.actionComment">· {{ h.actionComment }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </section>

    <!-- 手动触发对话框 -->
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
  Sparkles, Activity, Clock,
  BellOff, X, CheckCircle2,
  ChevronDown
} from 'lucide-vue-next'
import AiSummaryCard from '@/components/alert/AiSummaryCard.vue'
import ThinkingPanel from '@/components/ai/ThinkingPanel.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
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
  return { total, pending, recovered, critical, serious }
})

const statusTabs = computed(() => [
  { value: '', label: '全部', hint: '所有状态', count: allEvents.value.length },
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

function setStatus(v: string) { filters.eventStatus = v }

async function openDetail(ev: AlertEventItem) {
  detail.value = await getAlertEvent(ev.id)
}

async function onAction(action: 'CONFIRM' | 'RECOVER' | 'CLOSE') {
  if (!detail.value) return
  await handleAlertEvent({ eventId: detail.value.id, actionType: action, operatorName: 'admin' })
  ElMessage.success('操作已记录')
  await Promise.all([loadAll(), openDetail(detail.value)])
}

async function onRefreshSummary() {
  if (!detail.value) return
  summaryLoading.value = true
  try {
    await summarizeAlertEvent(detail.value.id)
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

watch(() => realtime.lastEventId, () => loadAll())
watch(() => realtime.aiSummaryUpdates, async () => {
  if (detail.value) {
    detail.value = await getAlertEvent(detail.value.id)
  }
})

// ----- 触发演示 -----
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

async function onStartStory() {
  const rules = await listAlertRules({ status: 'ENABLED' })
  if (!rules.length) {
    ElMessage.warning('没有启用的告警规则，请先创建规则')
    return
  }
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

// ----- helpers -----
function evStatusName(s?: string) {
  return ({ PENDING: '待处理', CONFIRMED: '已确认', RECOVERED: '已恢复', CLOSED: '已关闭' } as Record<string, string>)[s || ''] || s || '-'
}
function evStatusClass(s?: string) {
  if (s === 'PENDING') return 'pending'
  if (s === 'CONFIRMED') return 'confirmed'
  if (s === 'RECOVERED') return 'recovered'
  if (s === 'CLOSED') return 'closed'
  return ''
}
function notifyStatusName(s?: string) {
  return ({ PENDING: '发送中', SUCCESS: '成功', FAILED: '失败' } as Record<string, string>)[s || ''] || s || '-'
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
.events-v {
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

.hero-eyebrow .dot-anim {
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
  color: var(--text-primary);
  line-height: 0.85;
  font-variant-numeric: tabular-nums;
}

.hero-num.urgent { color: var(--accent); }

.hero-words { padding-bottom: 6px; }

.hero-line-1 {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.hero-line-2 {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.hero-right {
  display: flex;
  align-items: center;
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

.hero-action:hover {
  border-color: var(--accent-line);
  color: var(--accent);
}

.hero-action.primary {
  background: var(--accent-soft);
  border-color: var(--accent-line);
  color: var(--accent);
}

.hero-action.primary:hover {
  background: var(--accent);
  color: var(--bg-base);
  border-color: var(--accent);
}

/* ========== Layout ========== */
.layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1.05fr) minmax(0, 1.15fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1280px) {
  .layout { grid-template-columns: 220px minmax(0, 1fr); }
  .detail { display: none; }
}

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}

/* 左侧 */
.side {
  position: sticky;
  top: 80px;
  display: grid;
  gap: 18px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  align-content: start;
}

.side-section { display: grid; gap: 8px; }

.side-section .eyebrow {
  margin-bottom: 4px;
}

.side-section .live-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ok);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.tabs { display: grid; gap: 2px; }

.tab {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  align-items: center;
  gap: 0 10px;
  padding: 9px 11px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: all 0.12s ease;
}

.tab:hover {
  background: var(--bg-elev-2);
}

.tab.active {
  background: var(--accent-soft);
  border-color: var(--accent-line);
}

.tab-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.tab-hint {
  grid-column: 1 / 2;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.tab-count {
  grid-row: 1 / 3;
  align-self: center;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-faint);
}

.tab.active .tab-count { color: var(--accent); }

.live-list { display: grid; gap: 0; }

.live-row {
  display: grid;
  grid-template-columns: 2px 1fr;
  gap: 10px;
  padding: 8px 0;
  border-top: 1px dashed var(--line);
}

.live-row:first-child { border-top: 0; }

.live-bar {
  border-radius: 2px;
}

.live-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-sub {
  margin-top: 2px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);
}

/* 中间事件列表 */
.main { display: grid; gap: 12px; }

.filters { display: flex; gap: 8px; }
.filter-select { width: 130px; }
.filter-input  { flex: 1; }

.event-list {
  display: grid;
  gap: 8px;
  min-height: 200px;
}

.event-card {
  display: grid;
  grid-template-columns: 3px 1fr;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  cursor: pointer;
  transition: all 0.15s ease;
}

.event-card:hover {
  border-color: var(--line-strong);
  background: var(--bg-elev-2);
  transform: translateY(-1px);
}

.event-card.active {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.lv-bar { border-radius: 3px; }

.ev-body { display: grid; gap: 6px; min-width: 0; }

.ev-row1 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.ev-no {
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.lv-tag {
  padding: 1px 7px;
  border: 1px solid;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  font-weight: 500;
}

.st-pill {
  padding: 1px 7px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.04em;
  border: 1px solid transparent;
}

.st-pill.pending   { color: var(--warn);  background: var(--warn-soft); }
.st-pill.confirmed { color: var(--accent); background: var(--accent-soft); }
.st-pill.recovered { color: var(--ok);    background: var(--ok-soft); }
.st-pill.closed    { color: var(--text-muted); background: var(--bg-elev-3); }

.ai-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 7px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.ai-pill.pending {
  border-color: rgba(251, 191, 36, 0.35);
  background: var(--warn-soft);
  color: var(--warn);
}

.time {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-faint);
}

.ev-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ev-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.ev-meta .obj { color: var(--text-secondary); }
.ev-meta .sep { color: var(--text-faint); }

.ev-meta code {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-elev-3);
  color: var(--text-secondary);
  font-size: 10.5px;
}

.empty {
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

.empty-hint { font-size: 12px; }

/* 右侧详情 */
.detail {
  position: sticky;
  top: 80px;
  max-height: calc(100vh - 100px);
  overflow: auto;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  display: grid;
  gap: 16px;
  align-content: start;
}

.close-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.close-btn:hover {
  border-color: var(--line-strong);
  color: var(--text-primary);
}

.detail-head {
  display: grid;
  grid-template-columns: 3px 1fr;
  gap: 14px;
  padding-right: 36px;
}

.lv-strip {
  border-radius: 3px;
}

.detail-head .eyebrow { margin-bottom: 4px; }

.detail-title {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.01em;
  line-height: 1.4;
}

.detail-sub {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions-bar {
  display: flex;
  gap: 6px;
}

.op-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.op-btn:hover:not(:disabled) {
  background: var(--bg-elev-3);
  color: var(--text-primary);
}

.op-btn.primary {
  background: var(--accent-soft);
  border-color: var(--accent-line);
  color: var(--accent);
}

.op-btn.primary:hover:not(:disabled) {
  background: var(--accent);
  color: var(--bg-base);
  border-color: var(--accent);
}

.op-btn.danger {
  color: var(--danger);
  border-color: rgba(248, 113, 113, 0.35);
}

.op-btn.danger:hover:not(:disabled) {
  background: var(--danger-soft);
}

.op-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.kv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
}

.kv-grid > div { display: grid; gap: 3px; }

.kv-grid .k {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-muted);
}

.kv-grid .v {
  font-size: 13px;
  color: var(--text-primary);
}

.kv-grid .v code {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-elev-3);
  font-family: var(--font-mono);
  font-size: 11.5px;
}

.kv-grid .v .muted { color: var(--text-muted); margin-left: 4px; }

.logs { display: grid; gap: 6px; }
.logs .eyebrow { margin-bottom: 4px; }

.log-item {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
}

.log-icon {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  margin-top: 1px;
}

.log-line { display: flex; gap: 8px; align-items: baseline; }
.log-line strong { font-size: 12px; font-weight: 500; color: var(--text-primary); }
.log-line .muted { color: var(--text-muted); font-size: 11px; }

.log-line2 {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
  font-family: var(--font-mono);
  font-size: 11px;
}
.log-line2 .muted { color: var(--text-muted); }
.log-line2 .err { color: var(--danger); }

.notify-pill {
  padding: 1px 7px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
}

.notify-pill.ok      { background: var(--ok-soft); color: var(--ok); }
.notify-pill.fail    { background: var(--danger-soft); color: var(--danger); }
.notify-pill.pending { background: var(--warn-soft); color: var(--warn); }

.full { width: 100%; }
</style>
