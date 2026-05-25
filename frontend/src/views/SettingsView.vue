<template>
  <div class="settings-view">
    <PageHeader
      eyebrow="SYSTEM SETTINGS"
      title="系统设置"
      subtitle="配置 LLM 模型，支持 OpenAI / DeepSeek / Qwen 等兼容 OpenAI 协议的服务。设置默认模型后，AI 建规则、告警摘要等能力即可启用。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增 LLM 配置</el-button>
      </template>
    </PageHeader>

    <!-- 状态提示 -->
    <section class="hint-banner" :class="hasDefault ? 'ok' : 'warn'">
      <component :is="hasDefault ? CheckIcon : AlertIcon" :size="16" />
      <span v-if="hasDefault">
        当前默认模型：<strong>{{ defaultConfig?.configName }}</strong>
        ({{ defaultConfig?.provider }} · {{ defaultConfig?.modelName }})
        — AI 能力已启用。
      </span>
      <span v-else>
        尚未设置默认模型 — AI 建规则等能力暂不可用。点击"新增 LLM 配置"添加一个。
      </span>
    </section>

    <!-- 列表 -->
    <section v-loading="loading" class="card-grid"
      :style="{ minHeight: list.length ? 'auto' : '240px' }">
      <article
        v-for="item in list"
        :key="item.id"
        class="llm-card"
        :class="{ 'is-default': item.isDefault }"
      >
        <header class="head">
          <div class="icon">
            <BrainIcon :size="18" />
          </div>
          <div class="meta">
            <div class="name-row">
              <span class="name">{{ item.configName }}</span>
              <span v-if="item.isDefault" class="default-pill">
                <BadgeCheck :size="11" />
                默认
              </span>
            </div>
            <div class="sub">
              <span class="provider">{{ item.provider }}</span>
              <span class="dot-sep" />
              <span class="model">{{ item.modelName }}</span>
            </div>
          </div>
          <span class="status-pill" :class="item.status === 'ENABLED' ? 'on' : 'off'">
            <span class="dot" />
            {{ item.status === 'ENABLED' ? '启用' : '停用' }}
          </span>
        </header>

        <div class="row">
          <span class="lbl">Base URL</span>
          <code class="val mono">{{ item.baseUrl }}</code>
        </div>
        <div class="row">
          <span class="lbl">API Key</span>
          <span class="val mono">{{ item.apiKeyMasked || '未设置' }}</span>
        </div>
        <div class="row">
          <span class="lbl">温度 / Token</span>
          <span>{{ item.temperature ?? '-' }} / {{ item.maxTokens ?? '-' }}</span>
        </div>

        <p v-if="item.description" class="desc">{{ item.description }}</p>

        <footer class="actions">
          <el-button v-if="!item.isDefault" text @click="onSetDefault(item)">
            <Crown :size="14" />&nbsp;设为默认
          </el-button>
          <el-button text @click="openEdit(item)">
            <EditIcon :size="14" />&nbsp;编辑
          </el-button>
          <el-button text :loading="testingId === item.id" @click="onTest(item)">
            <Zap :size="14" />&nbsp;测试连通
          </el-button>
          <el-popconfirm title="确认删除该配置？删除后 AI 能力可能不可用" confirm-button-text="删除"
            cancel-button-text="取消" @confirm="onDelete(item)">
            <template #reference>
              <el-button text type="danger">
                <TrashIcon :size="14" />&nbsp;删除
              </el-button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <div v-if="!loading && list.length === 0" class="empty">
        <div class="empty-icon"><BrainIcon :size="36" /></div>
        <div class="empty-title">还没有 LLM 配置</div>
        <div class="empty-hint">配置一个 OpenAI 兼容的服务（GPT-4o / DeepSeek / Qwen 等），AI 建规则就能用。</div>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增 LLM 配置</el-button>
      </div>
    </section>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑 LLM 配置' : '新增 LLM 配置'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <!-- 快速预设 -->
        <div v-if="!form.id" class="preset-row">
          <span class="preset-label">快速预设</span>
          <button
            v-for="p in presets"
            :key="p.label"
            class="preset-chip"
            type="button"
            @click="applyPreset(p)"
          >
            <component :is="Sparkles" :size="12" />
            {{ p.label }}
          </button>
        </div>

        <div class="form-grid">
          <el-form-item label="配置名称" prop="configName" class="span-2">
            <el-input v-model="form.configName" placeholder="例如：DeepSeek-Chat 主模型" />
          </el-form-item>
          <el-form-item label="提供商">
            <el-select v-model="form.provider" class="full">
              <el-option label="OpenAI" value="OPENAI" />
              <el-option label="DeepSeek" value="DEEPSEEK" />
              <el-option label="通义千问 Qwen" value="QWEN" />
              <el-option label="智谱 GLM" value="ZHIPU" />
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
                <el-option label="deepseek-chat" value="deepseek-chat" />
                <el-option label="deepseek-reasoner" value="deepseek-reasoner" />
              </el-option-group>
              <el-option-group label="Qwen">
                <el-option label="qwen-plus" value="qwen-plus" />
                <el-option label="qwen-turbo" value="qwen-turbo" />
                <el-option label="qwen-max" value="qwen-max" />
              </el-option-group>
              <el-option-group label="智谱 GLM">
                <el-option label="glm-5.1 (旗舰)" value="glm-5.1" />
                <el-option label="glm-4.6" value="glm-4.6" />
                <el-option label="glm-4.5-flash" value="glm-4.5-flash" />
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus as PlusIcon,
  RefreshCw as RefreshIcon,
  Edit3 as EditIcon,
  Trash2 as TrashIcon,
  Brain as BrainIcon,
  CheckCircle2 as CheckIcon,
  AlertTriangle as AlertIcon,
  BadgeCheck,
  Crown,
  Sparkles,
  Zap
} from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  deleteLlmConfig,
  listLlmConfigs,
  saveLlmConfig,
  setDefaultLlmConfig,
  testLlmConfig,
  type LlmModelConfigItem
} from '@/api/llmConfig'

const list = ref<LlmModelConfigItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const testingId = ref<number>()

interface Preset {
  label: string
  provider: string
  baseUrl: string
  modelName: string
  configName: string
}

const presets: Preset[] = [
  {
    label: '智谱 GLM-5.1',
    provider: 'ZHIPU',
    baseUrl: 'https://open.bigmodel.cn/api/coding/paas/v4',
    modelName: 'glm-5.1',
    configName: '智谱 GLM-5.1 主模型'
  },
  {
    label: 'DeepSeek Chat',
    provider: 'DEEPSEEK',
    baseUrl: 'https://api.deepseek.com/v1',
    modelName: 'deepseek-chat',
    configName: 'DeepSeek Chat'
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
  provider: 'OPENAI',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  modelName: 'gpt-4o-mini',
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
    // 编辑且未填新 key 时，不传 apiKey 字段，由后端保留原值
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
  try {
    const r = await testLlmConfig(item.id)
    if (r.success) {
      ElMessage.success(`连通成功（${r.durationMs}ms）：${r.reply || ''}`)
    } else {
      ElMessage.error('连通失败：' + (r.error || '未知错误'))
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
.settings-view { display: grid; gap: 16px; }

.hint-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 13px;
}

.hint-banner.ok {
  border: 1px solid rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.08);
  color: #6EE7B7;
}

.hint-banner.warn {
  border: 1px solid rgba(245, 158, 11, 0.3);
  background: rgba(245, 158, 11, 0.08);
  color: #FCD34D;
}

.hint-banner strong { color: var(--text-primary); }

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
}

.llm-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-panel);
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.llm-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-subtle);
  box-shadow: 0 16px 30px -20px rgba(0, 0, 0, 0.6);
}

.llm-card.is-default {
  border-color: rgba(59, 130, 246, 0.4);
  background:
    radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.10), transparent 60%),
    var(--bg-panel);
}

.head { display: flex; align-items: center; gap: 10px; }

.icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: linear-gradient(135deg, #3B82F6, #8B5CF6);
  color: white;
  flex-shrink: 0;
}

.meta { flex: 1; min-width: 0; }

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  color: var(--text-primary);
  font-weight: 600;
  font-size: 14px;
}

.default-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.18);
  color: #93C5FD;
  font-size: 11px;
  font-weight: 600;
}

.sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.dot-sep {
  width: 3px; height: 3px;
  background: var(--text-muted);
  border-radius: 50%;
  opacity: 0.5;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
}

.status-pill.on {
  background: rgba(16, 185, 129, 0.12);
  color: #6EE7B7;
}

.status-pill.off {
  background: rgba(148, 163, 184, 0.12);
  color: #94A3B8;
}

.status-pill .dot {
  width: 6px; height: 6px;
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

.lbl { color: var(--text-muted); }

.val.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.desc {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
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

.empty-title { color: var(--text-primary); font-size: 15px; font-weight: 600; }
.empty-hint  { color: var(--text-muted); margin-bottom: 8px; }

.preset-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-subtle);
}

.preset-label {
  color: var(--text-muted);
  font-size: 12px;
  margin-right: 4px;
}

.preset-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-panel);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(59, 130, 246, 0.08);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.span-2 { grid-column: span 2; }
.full   { width: 100%; }
</style>
