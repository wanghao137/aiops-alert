<template>
  <div class="settings-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">SYSTEM SETTINGS</span>
          <span class="dot-anim" :class="{ ok: hasDefault }" />
          <span class="hero-time">{{ hasDefault ? 'AI 能力已启用' : 'AI 能力未启用' }}</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num">{{ list.length }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ list.length > 0 ? '个 LLM 配置就绪' : '尚未配置 LLM' }}</div>
            <div class="hero-line-2">
              <template v-if="hasDefault">
                默认 · {{ defaultConfig?.configName }} · {{ defaultConfig?.modelName }}
              </template>
              <template v-else>
                添加 DeepSeek / OpenAI / Qwen 兼容协议的服务，启用 AI 建规则与告警摘要
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <button class="hero-action ghost" @click="loadList">
          <RefreshIcon :size="13" :stroke-width="1.6" /> 刷新
        </button>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.6" /> 新增 LLM
        </button>
      </div>
    </section>

    <!-- ========== Status banner ========== -->
    <section class="status-banner" :class="hasDefault ? 'ok' : 'warn'">
      <span class="banner-icon">
        <component :is="hasDefault ? CheckIcon : AlertIcon" :size="13" :stroke-width="1.8" />
      </span>
      <div class="banner-text">
        <template v-if="hasDefault">
          当前默认模型：<strong>{{ defaultConfig?.configName }}</strong>
          <span class="banner-meta">{{ defaultConfig?.provider }} · {{ defaultConfig?.modelName }}</span>
        </template>
        <template v-else>
          尚未设置默认模型 — AI 建规则等能力暂不可用，添加一个 LLM 配置后即可启用。
        </template>
      </div>
    </section>

    <!-- ========== LLM Cards ========== -->
    <section v-loading="loading" class="llm-list"
      :style="{ minHeight: list.length ? 'auto' : '240px' }">
      <article
        v-for="item in list"
        :key="item.id"
        class="llm-card"
        :class="{ 'is-default': item.isDefault, disabled: item.status !== 'ENABLED' }"
      >
        <!-- 左：大模型标识 -->
        <div class="card-left">
          <div class="provider-badge" :class="providerClass(item.provider)">
            <component :is="providerIcon(item.provider)" :size="14" :stroke-width="1.7" />
            <span>{{ item.provider }}</span>
          </div>
          <div class="model-display">{{ item.modelName }}</div>
          <div class="config-name">{{ item.configName }}</div>

          <div class="status-tags">
            <span v-if="item.isDefault" class="tag default">
              <Crown :size="10" :stroke-width="1.8" />
              DEFAULT
            </span>
            <span class="tag" :class="item.status === 'ENABLED' ? 'on' : 'off'">
              <span class="tag-dot" />
              {{ item.status === 'ENABLED' ? 'ENABLED' : 'DISABLED' }}
            </span>
          </div>
        </div>

        <!-- 右：配置详情 -->
        <div class="card-right">
          <div class="kv-row">
            <span class="kv-key">BASE URL</span>
            <code class="kv-val">{{ item.baseUrl }}</code>
          </div>
          <div class="kv-row">
            <span class="kv-key">API KEY</span>
            <code class="kv-val muted">{{ item.apiKeyMasked || '— 未设置 —' }}</code>
          </div>
          <div class="kv-row split">
            <div>
              <span class="kv-key">TEMPERATURE</span>
              <span class="kv-val tabular-nums">{{ item.temperature ?? '-' }}</span>
            </div>
            <div>
              <span class="kv-key">MAX TOKENS</span>
              <span class="kv-val tabular-nums">{{ item.maxTokens ?? '-' }}</span>
            </div>
          </div>
          <p v-if="item.description" class="card-desc">{{ item.description }}</p>

          <footer class="card-actions">
            <button v-if="!item.isDefault" class="act-btn" @click="onSetDefault(item)">
              <Crown :size="12" :stroke-width="1.7" />设为默认
            </button>
            <button class="act-btn" @click="openEdit(item)">
              <EditIcon :size="12" :stroke-width="1.7" />编辑
            </button>
            <button class="act-btn" :disabled="testingId === item.id" @click="onTest(item)">
              <span v-if="testingId === item.id" class="mini-spinner" />
              <Zap v-else :size="12" :stroke-width="1.7" />
              {{ testingId === item.id ? '测试中…' : '测试连通' }}
            </button>
            <el-popconfirm title="确认删除该配置？删除后 AI 能力可能不可用" confirm-button-text="删除"
              cancel-button-text="取消" @confirm="onDelete(item)">
              <template #reference>
                <button class="act-btn danger">
                  <TrashIcon :size="12" :stroke-width="1.7" />删除
                </button>
              </template>
            </el-popconfirm>
          </footer>
        </div>
      </article>

      <div v-if="!loading && list.length === 0" class="empty">
        <BrainIcon :size="28" :stroke-width="1.4" />
        <div class="empty-title">还没有 LLM 配置</div>
        <div class="empty-hint">配置一个 OpenAI 兼容的服务（DeepSeek / GPT-4o / Qwen），AI 能力就能用。</div>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.7" /> 新增 LLM 配置
        </button>
      </div>
    </section>

    <!-- ========== 编辑对话框 ========== -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑 LLM 配置' : '新增 LLM 配置'"
      width="660px"
      :close-on-click-modal="false"
      class="llm-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <!-- 快速预设 -->
        <div v-if="!form.id" class="preset-row">
          <span class="preset-label">PRESETS</span>
          <button
            v-for="p in presets"
            :key="p.label"
            class="preset-chip"
            type="button"
            @click="applyPreset(p)"
          >
            <Sparkles :size="11" :stroke-width="1.8" />
            {{ p.label }}
          </button>
        </div>

        <div class="form-grid">
          <el-form-item label="配置名称" prop="configName" class="span-2">
            <el-input v-model="form.configName" placeholder="例如：DeepSeek V4 Flash 主模型" />
          </el-form-item>
          <el-form-item label="提供商">
            <el-select v-model="form.provider" class="full">
              <el-option label="DeepSeek" value="DEEPSEEK" />
              <el-option label="OpenAI" value="OPENAI" />
              <el-option label="通义千问 Qwen" value="QWEN" />
              <el-option label="Custom (兼容 OpenAI)" value="CUSTOM" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型名" prop="modelName">
            <el-select
              v-model="form.modelName"
              filterable
              allow-create
              default-first-option
              placeholder="例如：gpt-4o-mini"
              class="full"
            >
              <el-option-group label="OpenAI">
                <el-option label="gpt-4o-mini" value="gpt-4o-mini" />
                <el-option label="gpt-4o" value="gpt-4o" />
                <el-option label="gpt-4.1-mini" value="gpt-4.1-mini" />
              </el-option-group>
              <el-option-group label="DeepSeek">
                <el-option label="deepseek-v4-flash" value="deepseek-v4-flash" />
                <el-option label="deepseek-v4-pro" value="deepseek-v4-pro" />
              </el-option-group>
              <el-option-group label="Qwen">
                <el-option label="qwen-plus" value="qwen-plus" />
                <el-option label="qwen-turbo" value="qwen-turbo" />
                <el-option label="qwen-max" value="qwen-max" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="Base URL" prop="baseUrl" class="span-2">
            <el-input v-model="form.baseUrl" placeholder="例如：https://api.openai.com/v1" />
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey" class="span-2">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              :placeholder="form.id ? '留空则保留原 API Key' : '请输入 API Key'"
            />
          </el-form-item>
          <el-form-item label="Temperature">
            <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2"
              controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="Max Tokens">
            <el-input-number v-model="form.maxTokens" :min="2048" :max="131072" :step="1024"
              controls-position="right" class="full" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="设为默认" class="span-2">
            <el-switch v-model="form.isDefault" active-text="作为默认模型，所有 AI 能力将使用此配置" />
          </el-form-item>
          <el-form-item label="备注" class="span-2">
            <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 测试连通结果 ========== -->
    <el-dialog
      v-model="testDialogVisible"
      title="LLM 测试连通结果"
      width="660px"
      class="llm-dialog"
    >
      <div v-if="testResult" class="test-result">
        <div class="test-banner" :class="testResult.success ? 'ok' : 'fail'">
          <span class="banner-icon">
            <component :is="testResult.success ? CheckIcon : AlertIcon" :size="13" :stroke-width="1.8" />
          </span>
          <div class="banner-content">
            <strong>{{ testResult.success ? '连通成功' : '连通失败' }}</strong>
            <div class="banner-meta">
              <span v-if="testResult.modelName">{{ testResult.modelName }}</span>
              <span v-if="testResult.durationMs" class="sep">·</span>
              <span v-if="testResult.durationMs" class="tabular-nums">{{ testResult.durationMs }}ms</span>
            </div>
          </div>
        </div>

        <ThinkingPanel
          v-if="testResult.reasoning"
          :content="testResult.reasoning"
          :duration-ms="testResult.durationMs"
          title="模型思考过程"
          :default-expanded="true"
        />

        <div v-if="testResult.reply" class="reply-card">
          <div class="reply-label">MODEL REPLY</div>
          <div class="reply-text">{{ testResult.reply }}</div>
        </div>

        <div v-if="testResult.error" class="error-card">
          <div class="reply-label">ERROR</div>
          <pre class="error-text">{{ testResult.error }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="testDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>


<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus as PlusIcon,
  RefreshCw as RefreshIcon,
  Edit3 as EditIcon,
  Trash2 as TrashIcon,
  Brain as BrainIcon,
  CheckCircle2 as CheckIcon,
  AlertTriangle as AlertIcon,
  Crown,
  Sparkles,
  Zap,
  Cpu,
  Bot,
  Globe
} from 'lucide-vue-next'
import ThinkingPanel from '@/components/ai/ThinkingPanel.vue'
import {
  deleteLlmConfig,
  listLlmConfigs,
  saveLlmConfig,
  setDefaultLlmConfig,
  testLlmConfig,
  type LlmModelConfigItem,
  type LlmTestResult
} from '@/api/llmConfig'

const list = ref<LlmModelConfigItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const testingId = ref<number>()
const testDialogVisible = ref(false)
const testResult = ref<LlmTestResult>()

interface Preset {
  label: string
  provider: string
  baseUrl: string
  modelName: string
  configName: string
}

const presets: Preset[] = [
  {
    label: 'DeepSeek V4 Flash',
    provider: 'DEEPSEEK',
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-v4-flash',
    configName: 'DeepSeek V4 Flash 主模型'
  },
  {
    label: 'DeepSeek V4 Pro',
    provider: 'DEEPSEEK',
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-v4-pro',
    configName: 'DeepSeek V4 Pro 高质量模型'
  },
  {
    label: 'OpenAI GPT-4o-mini',
    provider: 'OPENAI',
    baseUrl: 'https://api.openai.com/v1',
    modelName: 'gpt-4o-mini',
    configName: 'OpenAI GPT-4o-mini'
  },
  {
    label: 'Qwen-Plus',
    provider: 'QWEN',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'qwen-plus',
    configName: 'Qwen-Plus 主模型'
  }
]

const emptyForm = (): LlmModelConfigItem => ({
  configName: '',
  provider: 'DEEPSEEK',
  baseUrl: 'https://api.deepseek.com',
  apiKey: '',
  modelName: 'deepseek-v4-flash',
  temperature: 0.2,
  maxTokens: 8192,
  isDefault: false,
  status: 'ENABLED',
  description: ''
})

const form = reactive<LlmModelConfigItem>(emptyForm())

const rules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 base URL', trigger: 'blur' }],
  modelName: [{ required: true, message: '请选择或输入模型名', trigger: 'change' }]
}

const defaultConfig = computed(() => list.value.find((i) => i.isDefault && i.status === 'ENABLED'))
const hasDefault = computed(() => !!defaultConfig.value)

function providerIcon(p?: string): Component {
  const map: Record<string, Component> = {
    OPENAI: Sparkles,
    DEEPSEEK: Cpu,
    QWEN: Bot,
    CUSTOM: Globe
  }
  return map[p || ''] || Globe
}

function providerClass(p?: string) {
  return `p-${(p || 'CUSTOM').toLowerCase()}`
}

async function loadList() {
  loading.value = true
  try {
    list.value = await listLlmConfigs()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm(), { isDefault: list.value.length === 0 })
  dialogVisible.value = true
}

function openEdit(item: LlmModelConfigItem) {
  Object.assign(form, emptyForm(), {
    ...item,
    apiKey: '' // 编辑时不回显 key
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: LlmModelConfigItem = { ...form }
    if (form.id && !form.apiKey) {
      const original = list.value.find((i) => i.id === form.id)
      if (original) payload.apiKey = ''
    }
    await saveLlmConfig(payload)
    ElMessage.success(form.id ? '已更新' : '已创建')
    dialogVisible.value = false
    await loadList()
  } finally {
    submitting.value = false
  }
}

async function onDelete(item: LlmModelConfigItem) {
  if (!item.id) return
  await deleteLlmConfig(item.id)
  ElMessage.success('已删除')
  await loadList()
}

async function onSetDefault(item: LlmModelConfigItem) {
  if (!item.id) return
  await setDefaultLlmConfig(item.id)
  ElMessage.success('已设为默认')
  await loadList()
}

async function onTest(item: LlmModelConfigItem) {
  if (!item.id) return
  testingId.value = item.id
  testResult.value = undefined
  try {
    testResult.value = await testLlmConfig(item.id)
    testDialogVisible.value = true
    if (testResult.value.success) {
      ElMessage.success(`连通成功（${testResult.value.durationMs}ms）`)
    } else {
      ElMessage.error('连通失败')
    }
  } finally {
    testingId.value = undefined
  }
}

function applyPreset(p: Preset) {
  form.provider = p.provider
  form.baseUrl = p.baseUrl
  form.modelName = p.modelName
  if (!form.configName) form.configName = p.configName
}

onMounted(loadList)
</script>


<style scoped>
.settings-v {
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
  background: var(--warn);
  animation: pulse-soft 2.4s ease-in-out infinite;
}

.dot-anim.ok { background: var(--ok); }

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

/* ========== Status banner ========== */
.status-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-left: 2px solid;
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
}

.status-banner.ok    { border-left-color: var(--ok); }
.status-banner.warn  { border-left-color: var(--warn); }

.banner-icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.status-banner.ok   .banner-icon { background: var(--ok-soft);   color: var(--ok); }
.status-banner.warn .banner-icon { background: var(--warn-soft); color: var(--warn); }

.banner-text {
  flex: 1;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.banner-text strong {
  color: var(--text-primary);
  font-weight: 500;
}

.banner-meta {
  margin-left: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

/* ========== LLM Cards ========== */
.llm-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 14px;
}

.llm-card {
  display: grid;
  grid-template-columns: minmax(160px, 220px) 1fr;
  gap: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  overflow: hidden;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.llm-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
}

.llm-card.is-default {
  border-color: var(--accent-line);
  background:
    radial-gradient(circle at 0% 0%, var(--accent-soft), transparent 60%),
    var(--bg-elev-1);
}

.llm-card.disabled .card-left,
.llm-card.disabled .card-right {
  opacity: 0.55;
}

.card-left {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 18px 16px 16px;
  background: var(--bg-elev-2);
  border-right: 1px solid var(--line);
}

.provider-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: max-content;
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.16em;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
}

.provider-badge.p-openai   { color: var(--ok);     border-color: rgba(52, 211, 153, 0.4); }
.provider-badge.p-deepseek { color: var(--accent); border-color: var(--accent-line); }
.provider-badge.p-qwen     { color: var(--warn);   border-color: rgba(251, 191, 36, 0.4); }
.provider-badge.p-custom   { color: var(--text-muted); }

.model-display {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--text-primary);
  word-break: break-word;
}

.config-name {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

.status-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 6px;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 7px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.1em;
}

.tag.default {
  background: var(--accent-soft);
  color: var(--accent);
  border: 1px solid var(--accent-line);
}

.tag.on  { background: var(--ok-soft);     color: var(--ok); }
.tag.off { background: var(--bg-elev-3);   color: var(--text-muted); }

.tag-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.tag.on .tag-dot { animation: pulse-soft 2s ease-in-out infinite; }

/* Right side */
.card-right {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 16px 18px;
}

.kv-row {
  display: grid;
  grid-template-columns: 100px 1fr;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.kv-row.split {
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.kv-row.split > div {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 8px;
  align-items: center;
}

.kv-key {
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.kv-val {
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kv-val.muted { color: var(--text-muted); }

.card-desc {
  margin: 4px 0 0;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.card-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  flex-wrap: wrap;
}

.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 9px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.act-btn:hover {
  background: var(--bg-elev-2);
  color: var(--text-primary);
}

.act-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.act-btn.danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.mini-spinner {
  width: 11px;
  height: 11px;
  border: 1.4px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

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
:deep(.llm-dialog .el-dialog) {
  background: var(--bg-elev-1);
  border: 1px solid var(--line);
  box-shadow: var(--inset);
}

:deep(.llm-dialog .el-dialog__title) {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.preset-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
}

.preset-label {
  margin-right: 4px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.preset-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev-1);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.span-2 { grid-column: span 2; }
.full   { width: 100%; }

/* ========== 测试连通结果 ========== */
.test-result {
  display: grid;
  gap: 12px;
}

.test-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid;
  border-left-width: 2px;
  border-radius: var(--radius-md);
}

.test-banner.ok {
  background: var(--ok-soft);
  border-color: rgba(52, 211, 153, 0.35);
  color: var(--ok);
}

.test-banner.fail {
  background: var(--danger-soft);
  border-color: rgba(248, 113, 113, 0.35);
  color: var(--danger);
}

.test-banner .banner-icon {
  background: rgba(255, 255, 255, 0.06);
  color: inherit;
}

.banner-content {
  display: grid;
  gap: 2px;
}

.banner-content strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
}

.banner-content .banner-meta {
  margin-left: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.banner-meta .sep { color: var(--text-faint); }

.reply-card,
.error-card {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-2);
}

.error-card {
  border-color: rgba(248, 113, 113, 0.3);
  border-left: 2px solid var(--danger);
  background: var(--danger-soft);
}

.reply-label {
  margin-bottom: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-muted);
}

.reply-text {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.error-text {
  margin: 0;
  color: var(--danger);
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Responsive */
@media (max-width: 760px) {
  .settings-v { padding: 0 14px 24px; }
  .hero-num { font-size: 64px; }
  .llm-card { grid-template-columns: 1fr; }
  .card-left { border-right: 0; border-bottom: 1px solid var(--line); }
  .form-grid { grid-template-columns: 1fr; }
  .span-2 { grid-column: span 1; }
}
</style>
