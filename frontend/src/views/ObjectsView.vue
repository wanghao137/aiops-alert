<template>
  <div class="objects-v">
    <!-- ========== HERO ========== -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-eyebrow">
          <span class="eyebrow">MONITOR OBJECTS</span>
          <span class="dot-anim" />
          <span class="hero-time">服务器 · 数据库 · 同步作业 · 加工作业</span>
        </div>
        <div class="hero-headline">
          <span class="hero-num">{{ stats?.total ?? 0 }}</span>
          <div class="hero-words">
            <div class="hero-line-1">{{ (stats?.total ?? 0) > 0 ? '个对象在监控范围内' : '尚未注册任何对象' }}</div>
            <div class="hero-line-2">
              启用 {{ stats?.enabled ?? 0 }}
              <template v-for="t in stats?.byType ?? []" :key="t.objectType">
                · {{ t.objectTypeName }} {{ t.total }}
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
          <PlusIcon :size="13" :stroke-width="1.6" /> 新增对象
        </button>
      </div>
    </section>

    <!-- ========== 类型分布卡 ========== -->
    <section v-if="stats && stats.total > 0" class="type-overview">
      <article
        v-for="t in OBJECT_TYPES"
        :key="t.value"
        class="type-cell"
        :class="{ active: filters.objectType === t.value }"
        @click="setType(filters.objectType === t.value ? '' : t.value)"
      >
        <div class="type-cell-head">
          <span class="type-icon" :style="{ color: t.color, background: hexToRgba(t.color, 0.1), borderColor: hexToRgba(t.color, 0.3) }">
            <component :is="t.icon" :size="14" :stroke-width="1.7" />
          </span>
          <span class="type-name">{{ t.label }}</span>
        </div>
        <div class="type-num tabular-nums">{{ countOf(t.value) }}</div>
        <div class="type-meta tabular-nums">
          启用 {{ countEnabledOf(t.value) }} · 占比 {{ stats.total ? Math.round(countOf(t.value) / stats.total * 100) : 0 }}%
        </div>
      </article>
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
            <SearchIcon :size="13" :stroke-width="1.7" />
          </template>
        </el-input>
      </div>
    </section>

    <!-- ========== Object Grid ========== -->
    <section v-loading="loading && list.length > 0" class="object-grid" :style="{ minHeight: list.length ? 'auto' : '240px' }">
      <SkeletonList v-if="loading && list.length === 0" :rows="6" variant="row" />
      <article
        v-for="item in list"
        :key="item.id"
        class="object-card"
        :class="{ disabled: item.status === 'DISABLED' }"
      >
        <span class="type-strip" :style="{ background: getObjectTypeMeta(item.objectType).color }" />

        <header class="card-head">
          <div class="object-icon" :style="{
            color: getObjectTypeMeta(item.objectType).color,
            background: hexToRgba(getObjectTypeMeta(item.objectType).color, 0.1),
            boxShadow: `0 0 0 1px ${hexToRgba(getObjectTypeMeta(item.objectType).color, 0.25)}, 0 0 28px -6px ${hexToRgba(getObjectTypeMeta(item.objectType).color, 0.5)}`
          }">
            <component :is="getObjectTypeMeta(item.objectType).icon" :size="18" :stroke-width="1.6" />
          </div>
          <div class="head-meta">
            <div class="object-name" :title="item.objectName">{{ item.objectName }}</div>
            <div class="sub-row">
              <span class="object-code">{{ item.objectCode }}</span>
              <span class="sep">·</span>
              <span class="type-label">{{ item.objectTypeName }}</span>
            </div>
          </div>
          <span :class="['st-pill', item.status === 'ENABLED' ? 'on' : 'off']">
            <span class="st-dot" />{{ item.status === 'ENABLED' ? '启用' : '停用' }}
          </span>
        </header>

        <div class="info-row">
          <span class="lbl">负责人</span>
          <span class="val">
            {{ item.ownerName || '—' }}
            <small v-if="item.ownerPhone" class="muted">· {{ item.ownerPhone }}</small>
          </span>
        </div>

        <div v-if="tagList(item.tags).length" class="tag-row">
          <span v-for="tag in tagList(item.tags)" :key="tag" class="tag-chip">{{ tag }}</span>
        </div>

        <p v-if="item.description" class="desc" :title="item.description">{{ item.description }}</p>

        <footer class="card-actions">
          <button class="act-btn" @click="openEdit(item)">
            <EditIcon :size="13" :stroke-width="1.7" />编辑
          </button>
          <button class="act-btn" @click="onToggle(item)">
            <PowerIcon :size="13" :stroke-width="1.7" />{{ item.status === 'ENABLED' ? '停用' : '启用' }}
          </button>
          <el-popconfirm
            title="确认删除该对象？关联的规则需要重新绑定。"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="onDelete(item)"
          >
            <template #reference>
              <button class="act-btn danger">
                <TrashIcon :size="13" :stroke-width="1.7" />删除
              </button>
            </template>
          </el-popconfirm>
        </footer>
      </article>

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty">
        <ServerCrashIcon :size="28" :stroke-width="1.4" />
        <div class="empty-title">暂无监控对象</div>
        <div class="empty-hint">点击右上角「新增对象」开始添加，对象是规则、事件、Incident 的根基。</div>
        <button class="hero-action primary" @click="openCreate">
          <PlusIcon :size="13" :stroke-width="1.7" /> 新增对象
        </button>
      </div>
    </section>

    <!-- ========== 编辑对话框 ========== -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑监控对象' : '新增监控对象'"
      width="640px"
      :close-on-click-modal="false"
      class="object-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <div class="section-title"><span class="num">01</span>基础信息</div>
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
        </div>

        <div class="section-title"><span class="num">02</span>分组与状态</div>
        <div class="form-grid">
          <el-form-item label="标签" class="span-2">
            <el-input v-model="form.tags" placeholder="多个标签用英文逗号分隔，如：prod,核心,7x24" />
          </el-form-item>
          <el-form-item label="状态" class="span-2">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">启用</el-radio-button>
              <el-radio-button label="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <div class="section-title"><span class="num">03</span>详细描述</div>
        <div class="form-grid">
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus as PlusIcon,
  RefreshCw as RefreshIcon,
  Search as SearchIcon,
  Edit3 as EditIcon,
  Power as PowerIcon,
  Trash2 as TrashIcon,
  ServerCrash as ServerCrashIcon
} from 'lucide-vue-next'
import { OBJECT_TYPES, getObjectTypeMeta } from '@/utils/objectType'
import SkeletonList from '@/components/common/SkeletonList.vue'
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

function countEnabledOf(type: string) {
  return stats.value?.byType.find((t) => t.objectType === type)?.enabled ?? 0
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
.objects-v {
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

/* ========== Type overview ========== */
.type-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.type-cell {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--bg-elev-1);
  box-shadow: var(--inset);
  cursor: pointer;
  transition: all 0.18s ease;
}

.type-cell:hover {
  border-color: var(--line-strong);
  transform: translateY(-2px);
}

.type-cell.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.type-cell-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.type-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1px solid;
  flex-shrink: 0;
}

.type-name {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.type-num {
  font-family: var(--font-display);
  font-size: 38px;
  font-weight: 500;
  letter-spacing: -0.04em;
  color: var(--text-primary);
  line-height: 1;
}

.type-meta {
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

/* ========== Object grid ========== */
.object-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
  position: relative;
}

.object-card {
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

.object-card:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
}

.object-card.disabled { opacity: 0.55; }

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

.object-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.object-card:hover .object-icon {
  transform: scale(1.04);
}

.head-meta { flex: 1; min-width: 0; display: grid; gap: 3px; }

.object-name {
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

.object-code { color: var(--text-secondary); }
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
  padding: 8px 0;
  border-top: 1px dashed var(--line);
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

.muted {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 400;
}

/* Tags */
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.tag-chip {
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--bg-elev-2);
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.04em;
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

.empty-hint { font-size: 12px; margin-bottom: 8px; max-width: 360px; text-align: center; }

/* ========== Dialog ========== */
:deep(.object-dialog .el-dialog) {
  background: var(--bg-elev-1);
  border: 1px solid var(--line);
  box-shadow: var(--inset);
}

:deep(.object-dialog .el-dialog__title) {
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
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.span-2 { grid-column: span 2; }
.full { width: 100%; }

.opt-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* Responsive */
@media (max-width: 1280px) {
  .type-overview { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 760px) {
  .objects-v { padding: 0 14px 24px; }
  .hero-num { font-size: 64px; }
  .type-overview { grid-template-columns: 1fr; }
  .form-grid { grid-template-columns: 1fr; }
  .span-2 { grid-column: span 1; }
}
</style>
