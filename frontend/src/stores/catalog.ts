import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMetricCatalog, type MetricCatalog } from '@/api/metricCatalog'

/**
 * 指标字典 / 对象类型 / 比较符 / 告警级别 全局缓存。
 *
 * 几乎所有规则相关页面都要用到，统一 store 一次拉取多次复用。
 */
export const useCatalogStore = defineStore('catalog', () => {
  const data = ref<MetricCatalog>()
  const loading = ref(false)

  async function ensureLoaded(force = false) {
    if (!force && data.value) return data.value
    loading.value = true
    try {
      data.value = await getMetricCatalog()
      return data.value
    } finally {
      loading.value = false
    }
  }

  function metricsOfType(objectType?: string) {
    if (!objectType) return []
    return data.value?.metricsByType[objectType] || []
  }

  function findMetric(objectType?: string, metricCode?: string) {
    if (!objectType || !metricCode) return undefined
    return metricsOfType(objectType).find((m) => m.code === metricCode)
  }

  function findCompareOp(code?: string) {
    if (!code) return undefined
    return data.value?.compareOps.find((c) => c.code === code)
  }

  function levelTone(level?: string) {
    return data.value?.alertLevels.find((l) => l.value === level)?.tone || 'blue'
  }

  return { data, loading, ensureLoaded, metricsOfType, findMetric, findCompareOp, levelTone }
})
