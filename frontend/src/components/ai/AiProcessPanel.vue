<template>
  <section class="ai-process-panel" :class="panelState">
    <header class="process-head">
      <div>
        <div class="eyebrow">可解释 AI 流程</div>
        <h4>AI 分析过程</h4>
      </div>
      <span class="state-pill" :class="panelState">
        <component :is="stateIcon" :size="12" :stroke-width="1.8" />
        {{ stateLabel }}
      </span>
    </header>

    <div class="process-rail">
      <article
        v-for="step in steps"
        :key="step.key"
        class="process-step"
        :class="step.state"
      >
        <span class="step-index tabular-nums">{{ step.index }}</span>
        <span class="step-icon">
          <component :is="step.icon" :size="14" :stroke-width="1.7" />
        </span>
        <div class="step-body">
          <div class="step-title">{{ step.title }}</div>
          <p>{{ step.detail }}</p>
        </div>
      </article>
    </div>

    <footer class="process-foot">
      <span>展示的是系统可解释分析链路；模型原始推理内容仅在接口返回时单独折叠展示。</span>
      <span v-if="hasRawReasoning" class="raw-ok">已捕获原始推理</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  AlertTriangle,
  CheckCircle2,
  Circle,
  ClipboardList,
  GitBranch,
  ListChecks,
  Loader2,
  Radar
} from 'lucide-vue-next'
import type { AlertEventItem } from '@/api/alertEvent'

const props = defineProps<{
  event: AlertEventItem
  summaryStatus?: string
  hasRawReasoning?: boolean
}>()

const panelState = computed(() => {
  if (props.summaryStatus === 'FAILED') return 'failed'
  if (props.summaryStatus === 'SUCCESS') return 'success'
  return 'running'
})

const stateIcon = computed(() => {
  if (panelState.value === 'failed') return AlertTriangle
  if (panelState.value === 'success') return CheckCircle2
  return Loader2
})

const stateLabel = computed(() => {
  if (panelState.value === 'failed') return '生成失败'
  if (panelState.value === 'success') return '分析完成'
  return '分析中'
})

const eventValue = computed(() => {
  const current = props.event.currentValue || '未记录'
  const threshold = props.event.thresholdValue || '未记录'
  return `当前值 ${current}，阈值 ${threshold}`
})

const steps = computed(() => {
  const finalState = panelState.value
  const causeState = finalState === 'failed' ? 'failed' : finalState === 'success' ? 'done' : 'active'
  const actionState = finalState === 'success' ? 'done' : finalState === 'failed' ? 'failed' : 'waiting'
  return [
    {
      key: 'context',
      index: '01',
      title: '读取上下文',
      detail: `${props.event.objectName} · ${props.event.metricName} · ${eventValue.value}`,
      state: 'done',
      icon: ClipboardList
    },
    {
      key: 'impact',
      index: '02',
      title: '判断影响范围',
      detail: `${props.event.ruleName || '当前规则'} 触发 ${props.event.alertLevel} 级别告警，优先确认业务影响面。`,
      state: finalState === 'failed' ? 'failed' : 'done',
      icon: Radar
    },
    {
      key: 'cause',
      index: '03',
      title: '归因分析',
      detail: props.event.eventReason || '结合指标、阈值、对象标签和历史相似事件，归纳可能根因。',
      state: causeState,
      icon: GitBranch
    },
    {
      key: 'action',
      index: '04',
      title: '生成处置建议',
      detail: finalState === 'success'
        ? '已形成发生原因、影响范围和建议动作，可进入确认、恢复、关闭闭环。'
        : finalState === 'failed'
          ? '模型未完成摘要，可重新生成或切换模型后再试。'
          : '等待模型回写结构化摘要，完成后会自动刷新详情。',
      state: actionState,
      icon: finalState === 'success' ? ListChecks : Circle
    }
  ]
})
</script>

<style scoped>
.ai-process-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 10%, transparent), transparent 56%),
    var(--bg-elev-2);
  box-shadow: var(--inset);
}

.process-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.process-head h4 {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.state-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  padding: 3px 9px;
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.state-pill.running svg {
  animation: spin 1s linear infinite;
}

.state-pill.success {
  border-color: rgba(52, 211, 153, 0.28);
  background: var(--ok-soft);
  color: var(--ok);
}

.state-pill.failed {
  border-color: rgba(248, 113, 113, 0.35);
  background: var(--danger-soft);
  color: var(--danger);
}

.process-rail {
  display: grid;
  gap: 8px;
}

.process-step {
  position: relative;
  display: grid;
  grid-template-columns: 34px 28px 1fr;
  gap: 9px;
  align-items: start;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-1);
}

.step-index {
  padding-top: 2px;
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.step-icon {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg-elev-2);
  color: var(--text-muted);
}

.process-step.done .step-icon {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent);
}

.process-step.active .step-icon {
  border-color: rgba(251, 191, 36, 0.35);
  background: var(--warn-soft);
  color: var(--warn);
}

.process-step.failed .step-icon {
  border-color: rgba(248, 113, 113, 0.35);
  background: var(--danger-soft);
  color: var(--danger);
}

.process-step.waiting .step-icon {
  color: var(--text-faint);
}

.step-body {
  min-width: 0;
}

.step-title {
  color: var(--text-primary);
  font-size: 12.5px;
  font-weight: 500;
}

.step-body p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.process-foot {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-faint);
  font-size: 11.5px;
  line-height: 1.6;
}

.raw-ok {
  flex-shrink: 0;
  color: var(--ok);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 640px) {
  .process-head,
  .process-foot {
    display: grid;
  }

  .process-step {
    grid-template-columns: 30px 26px 1fr;
    padding: 10px;
  }
}
</style>
