<template>
  <div class="objects-view">
    <PageHeader
      eyebrow="MONITOR OBJECTS"
      title="监控对象管理"
      subtitle="维护服务器、数据库、同步作业、加工作业等具体实例。后续告警规则、事件、Incident 都基于这些对象触发。"
    >
      <template #actions>
        <el-button :icon="RefreshIcon" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增对象</el-button>
      </template>
    </PageHeader>

    <!-- 顶部统计卡 -->
    <section class="stat-row">
      <StatCard
        label="对象总数"
        :value="stats?.total ?? 0"
        :icon="LayersIcon"
        accent="#3B82F6"
        :hint="`其中 ${stats?.enabled ?? 0} 个启用`"
      />
      <StatCard
        v-for="t in stats?.byType ?? []"
        :key="t.objectType"
        :label="t.objectTypeName"
        :value="t.total"
        :icon="getObjectTypeMeta(t.objectType).icon"
        :accent="getObjectTypeMeta(t.objectType).color"
        :hint="`启用 ${t.enabled}`"
      />
    </section>

    <!-- 筛选条 -->
    <section class="toolbar">
      <div class="type-tabs">
        <button
          class="type-tab"
          :class="{ active: !filters.objectType }"
          @click="setType('')"
        >
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
        <el-select
          v-model="filters.status"
          placeholder="状态"
          clearable
          class="filter-select"
          @change="loadList"
        >
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索名称 / 编码 / 标签"
          clearable
          class="filter-input"
          @keyup.enter="loadList"
          @clear="loadList"
        >
          <template #prefix>
            <SearchIcon :size="14" />
          </template>
        </el-input>
      </div>
    </section>

    <!-- 列表（卡片网格） -->
    <section v-loading="loading" class="card-grid" :style="{ minHeight: list.length ? 'auto' : '240px' }">
      <article
        v-for="item in list"
        :key="item.id"
        class="object-card"
        :style="{ background: getObjectTypeMeta(item.objectType).gradient }"
      >
        <header class="object-card-head">
          <div class="object-icon" :style="{ background: hexToRgba(getObjectTypeMeta(item.objectType).color, 0.18), color: getObjectTypeMeta(item.objectType).color }">
            <component :is="getObjectTypeMeta(item.objectType).icon" :size="18" />
          </div>
          <div class="object-meta">
            <div class="object-name" :title="item.objectName">{{ item.objectName }}</div>
            <div class="object-code">{{ item.objectCode }}</div>
          </div>
          <span class="status-pill" :class="item.status === 'ENABLED' ? 'status-on' : 'status-off'">
            <span class="status-dot" />
            {{ item.status === 'ENABLED' ? '启用' : '停用' }}
          </span>
        </header>

        <div class="object-row">
          <span class="row-label">类型</span>
          <span>{{ item.objectTypeName }}</span>
        </div>
        <div class="object-row">
          <span class="row-label">负责人</span>
          <span>{{ item.ownerName || '-' }}<span v-if="item.ownerPhone" class="muted"> · {{ item.ownerPhone }}</span></span>
        </div>
        <div v-if="item.tags" class="tag-row">
          <span v-for="tag in tagList(item.tags)" :key="tag" class="tag-chip">{{ tag }}</span>
        </div>
        <p v-if="item.description" class="object-desc" :title="item.description">{{ item.description }}</p>

        <footer class="object-actions">
          <el-button text @click="openEdit(item)">
            <EditIcon :size="14" />&nbsp;编辑
          </el-button>
          <el-button text @click="onToggle(item)">
            <PowerIcon :size="14" />&nbsp;{{ item.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
          <el-popconfirm
            title="确认删除该对象？关联的规则需要重新绑定。"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="onDelete(item)"
          >
            <template #reference>
              <el-button text type="danger">
                <TrashIcon :size="14" />&nbsp;删除
              </el-button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty">
        <div class="empty-icon">
          <ServerCrashIcon :size="36" />
        </div>
        <div class="empty-title">暂无监控对象</div>
        <div class="empty-hint">点击右上角"新增对象"开始添加。</div>
        <el-button type="primary" :icon="PlusIcon" @click="openCreate">新增对象</el-button>
      </div>
    </section>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑监控对象' : '新增监控对象'"
      width="640px"
      :close-on-click-modal="false"
      class="object-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="对象名称" prop="objectName" class="span-2">
            <el-input v-model="form.objectName" placeholder="例如：生产 MySQL 主库" />
          </el-form-item>
          <el-form-item label="对象类型" prop="objectType">
            <el-select v-model="form.objectType" placeholder="请选择" class="full">
              <el-option v-for="t in OBJECT_TYPES" :key="t.value" :label="t.label" :value="t.value">
                <span class="opt-row">
                  <component :is="t.icon" :size="14" :style="{ color: t.color }" />
                  {{ t.label }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="对象编码">
            <el-input v-model="form.objectCode" placeholder="留空则自动生成" />
          </el-form-item>
          <el-form-item label="负责人">
            <el-input v-model="form.ownerName" placeholder="选填" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.ownerPhone" placeholder="选填" />
          </el-form-item>
          <el-form-item label="标签" class="span-2">
            <el-input v-model="form.tags" placeholder="多个标签用英文逗号分隔，如：prod,核心,7x24" />
          </el-form-item>
          <el-form-item label="状态" class="span-2">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="描述" class="span-2">
            <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="扩展配置 (JSON, 选填)" class="span-2">
            <el-input
              v-model="form.extConfig"
              type="textarea"
              :rows="3"
              placeholder='例如：{"ip":"10.0.0.21","port":3306,"env":"prod"}'
            />
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
  Search as SearchIcon,
  Layers as LayersIcon,
  Edit3 as EditIcon,
  Power as PowerIcon,
  Trash2 as TrashIcon,
  ServerCrash as ServerCrashIcon
} from 'lucide-vue-next'
import StatCard from '@/components/common/StatCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import {
  deleteMonitorObject,
  getMonitorObjectStats,
  listMonitorObjects,
  saveMonitorObject,
  toggleMonitorObject,
  type MonitorObjectItem,
  type MonitorObjectStats
} from '@/api/monitorObject'

const list = ref<MonitorObjectItem[]>([])
const stats = ref<MonitorObjectStats>()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const filters = reactive({
  objectType: '',
  status: '',
  keyword: ''
})

const emptyForm = (): MonitorObjectItem => ({
  objectName: '',
  objectType: 'SERVER',
  objectCode: '',
  ownerName: '',
  ownerPhone: '',
  tags: '',
  status: 'ENABLED',
  description: '',
  extConfig: ''
})

const form = reactive<MonitorObjectItem>(emptyForm())

const rules: FormRules = {
  objectName: [{ required: true, message: '请输入对象名称', trigger: 'blur' }],
  objectType: [{ required: true, message: '请选择对象类型', trigger: 'change' }]
}

function countOf(type: string) {
  return stats.value?.byType.find((t) => t.objectType === type)?.total ?? 0
}

async function loadList() {
  loading.value = true
  try {
    list.value = await listMonitorObjects({
      objectType: filters.objectType || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  stats.value = await getMonitorObjectStats()
}

async function loadAll() {
  await Promise.all([loadList(), loadStats()])
}

function setType(type: string) {
  filters.objectType = type
  loadList()
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(item: MonitorObjectItem) {
  Object.assign(form, emptyForm(), item)
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await saveMonitorObject({ ...form })
    ElMessage.success(form.id ? '已更新' : '已创建')
    dialogVisible.value = false
    await loadAll()
  } finally {
    submitting.value = false
  }
}

async function onToggle(item: MonitorObjectItem) {
  if (!item.id) return
  await toggleMonitorObject(item.id)
  ElMessage.success('已切换状态')
  await loadAll()
}

async function onDelete(item: MonitorObjectItem) {
  if (!item.id) return
  await deleteMonitorObject(item.id)
  ElMessage.success('已删除')
  await loadAll()
}

function tagList(tags?: string) {
  if (!tags) return []
  return tags.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
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
.objects-view {
  display: grid;
  gap: 16px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 1280px) {
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
  position: relative;
}

.object-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.object-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-subtle);
  box-shadow: 0 16px 30px -20px rgba(0, 0, 0, 0.6);
}

.object-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.object-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  flex-shrink: 0;
}

.object-meta {
  flex: 1;
  min-width: 0;
}

.object-name {
  color: var(--text-primary);
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.object-code {
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

.status-on {
  background: rgba(16, 185, 129, 0.12);
  color: #6EE7B7;
  border-color: rgba(16, 185, 129, 0.3);
}

.status-off {
  background: rgba(148, 163, 184, 0.12);
  color: #94A3B8;
  border-color: rgba(148, 163, 184, 0.3);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.object-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.row-label {
  color: var(--text-muted);
}

.muted {
  color: var(--text-muted);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.tag-chip {
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid var(--line-subtle);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 11px;
}

.object-desc {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.object-actions {
  display: flex;
  gap: 4px;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
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
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.span-2 {
  grid-column: span 2;
}

.full {
  width: 100%;
}

.opt-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
