import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'system'

const STORAGE_KEY = 'aiops:theme-mode'

/**
 * 主题切换 store。
 * - mode: 用户偏好（dark/light/system），持久化到 localStorage
 * - isDark: 实际应用的主题（system 模式下跟随 OS）
 *
 * 副作用：把主题应用到 <html data-theme="..."> 与 element-plus 用的 dark 类。
 */
export const useThemeStore = defineStore('theme', () => {
  const mq = window.matchMedia('(prefers-color-scheme: dark)')

  const mode = ref<ThemeMode>(readStored())
  const systemDark = ref(mq.matches)
  const isDark = computed(() =>
    mode.value === 'system' ? systemDark.value : mode.value === 'dark'
  )

  function readStored(): ThemeMode {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === 'dark' || v === 'light' || v === 'system' ? v : 'system'
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
  }

  // 监听系统主题变化，仅当 mode=system 时生效
  mq.addEventListener('change', (e) => {
    systemDark.value = e.matches
  })

  // 应用到 DOM
  watch(
    isDark,
    (dark) => {
      const html = document.documentElement
      const body = document.body
      html.dataset.theme = dark ? 'dark' : 'light'
      html.classList.toggle('dark', dark)
      body.classList.toggle('dark', dark)
    },
    { immediate: true }
  )

  return { mode, systemDark, isDark, setMode }
})
