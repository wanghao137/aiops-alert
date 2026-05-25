<template>
  <div v-if="visible" class="demo-banner">
    <div class="left">
      <div class="icon"><Wand2 :size="14" /></div>
      <div class="text">
        <div class="title">演示模式</div>
        <div class="hint">一键写入示例对象、渠道、规则和告警事件，便于快速预览</div>
      </div>
    </div>
    <div class="actions">
      <el-button :icon="RefreshIcon" :loading="busy === 'seed'" @click="onSeed">一键填充</el-button>
      <el-popconfirm
        title="确认清空所有业务数据？此操作不可恢复"
        confirm-button-text="清空"
        cancel-button-text="取消"
        @confirm="onClean"
      >
        <template #reference>
          <el-button :icon="TrashIcon" :loading="busy === 'clean'" type="danger" plain>清空</el-button>
        </template>
      </el-popconfirm>
      <button class="close" type="button" @click="visible = false" title="收起">
        <X :size="14" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Wand2, RefreshCw as RefreshIcon, Trash2 as TrashIcon, X } from 'lucide-vue-next'
import { seedDemoData, cleanDemoData } from '@/api/demo'

const emit = defineEmits<{ (e: 'changed'): void }>()
const visible = ref(true)
const busy = ref<'' | 'seed' | 'clean'>('')

async function onSeed() {
  busy.value = 'seed'
  try {
    const msg = await seedDemoData()
    ElMessage.success(msg)
    emit('changed')
  } finally {
    busy.value = ''
  }
}

async function onClean() {
  busy.value = 'clean'
  try {
    const msg = await cleanDemoData()
    ElMessage.success(msg)
    emit('changed')
  } finally {
    busy.value = ''
  }
}
</script>

<style scoped>
.demo-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 10px;
  background:
    linear-gradient(135deg, rgba(245, 158, 11, 0.08), rgba(245, 158, 11, 0.02));
  margin-bottom: 14px;
}

.left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.18);
  color: #FCD34D;
}

.title {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
}

.hint {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 1px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.close {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.close:hover {
  color: var(--text-primary);
  border-color: var(--line-subtle);
}
</style>
