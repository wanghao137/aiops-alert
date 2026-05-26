# UI 重设计 + 主题切换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有"工程师默认审美"风格 UI 升级为"Editorial × Terminal"作品级界面，并加入深色 / 浅色 / 跟随系统的主题切换能力。

**Architecture:** 用 CSS 变量 + 一组 `data-theme="dark|light"` 在 `<html>` 上，配合 Pinia store 持久化用户偏好；同时按页面逐个重做：Dashboard 已重做（样板），其他 6 页参照同一设计语言批量重写。Element Plus 通过 `dark` class + 主题变量适配，无需引第三方组件库。

**Tech Stack:** Vue 3 + TypeScript + Pinia + Element Plus + Tailwind + ECharts，引入字体 Space Grotesk + JetBrains Mono + Noto Sans SC（CDN）。

---

## File Structure

| 路径 | 责任 |
|---|---|
| `frontend/src/styles/global.css` | 设计 token：CSS 变量，分别定义 dark / light 调色板 |
| `frontend/src/stores/theme.ts` | 主题 store：持久化偏好、监听 system 变化、应用到 `<html data-theme>` |
| `frontend/src/composables/useTheme.ts` | 主题切换 composable，简化使用 |
| `frontend/src/components/layout/ThemeSwitcher.vue` | 三态切换按钮（深 / 浅 / 跟随系统） |
| `frontend/src/components/layout/AppHeader.vue` | 嵌入 ThemeSwitcher |
| `frontend/src/views/DashboardView.vue` | ✅ 已重做（参考样板） |
| `frontend/src/views/EventsView.vue` | 重做：左队列 + 中事件流 + 右详情 |
| `frontend/src/views/IncidentsView.vue` | 重做：故障组卡片 + 时间轴 |
| `frontend/src/views/RulesView.vue` | 重做：AI 横幅强化 + 规则卡片 |
| `frontend/src/views/ObjectsView.vue` | 重做：对象网格 + 类型分布 |
| `frontend/src/views/ChannelsView.vue` | 重做：渠道卡片 + 测试结果 |
| `frontend/src/views/SettingsView.vue` | 重做：LLM 配置卡片 + 测试 dialog |
| `frontend/src/components/alert/NlRuleDialog.vue` | 视觉对齐 |
| `frontend/src/components/alert/AiSummaryCard.vue` | 视觉对齐 |
| `frontend/src/components/ai/ThinkingPanel.vue` | 视觉对齐 |
| `frontend/src/components/command/CommandPalette.vue` | 视觉对齐 |

每个 view 文件 500-800 行，组件文件 100-300 行。

---

## Task 1: 主题切换基础设施

**Files:**
- Modify: `frontend/src/styles/global.css`（增加 light 主题 token）
- Create: `frontend/src/stores/theme.ts`
- Create: `frontend/src/composables/useTheme.ts`
- Create: `frontend/src/components/layout/ThemeSwitcher.vue`
- Modify: `frontend/src/components/layout/AppHeader.vue`（嵌入 switcher）
- Modify: `frontend/src/main.ts`（启动时应用主题）

- [ ] **Step 1.1：拓展 global.css，把 dark 变量收纳到 `:root[data-theme="dark"]`，新增 `:root[data-theme="light"]`。Element Plus 的 `dark` 类切换由 `<html>` 上的 class 控制。**

- [ ] **Step 1.2：创建 `stores/theme.ts`**

```ts
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'system'
const STORAGE_KEY = 'aiops:theme-mode'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem(STORAGE_KEY) as ThemeMode) || 'dark')
  const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)
  const isDark = computed(() => mode.value === 'system' ? systemDark.value : mode.value === 'dark')

  function setMode(next: ThemeMode) {
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
  }

  // 监听系统主题变化
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  mq.addEventListener('change', (e) => { systemDark.value = e.matches })

  // 应用到 DOM
  watch(isDark, (v) => {
    document.documentElement.dataset.theme = v ? 'dark' : 'light'
    document.documentElement.classList.toggle('dark', v)
    document.body.classList.toggle('dark', v)
  }, { immediate: true })

  return { mode, isDark, setMode }
})
```

- [ ] **Step 1.3：创建 ThemeSwitcher.vue（三态分段按钮）。**

- [ ] **Step 1.4：在 AppHeader 顶部右侧嵌入 ThemeSwitcher，放在 demo-btn 左边。**

- [ ] **Step 1.5：在 main.ts 顶部 `import './stores/theme'` 让 store 在启动时初始化（避免闪白）。**

- [ ] **Step 1.6：验证 — `npm run build` 无 error。手动切换三个主题 dark/light/system，看 `<html data-theme>` 变化与样式生效。**

- [ ] **Step 1.7：commit**

---

## Task 2: 重做 EventsView

**Files:**
- Modify: `frontend/src/views/EventsView.vue`

视觉要点：
- 顶部 hero：当前待处理 / 今日新增 / 紧急 / 处理中 4 个 metric，配 mini sparkline
- 左侧：状态侧栏（PENDING / CONFIRMED / RECOVERED / CLOSED）+ 实时事件流
- 中间：事件卡片列表，事件卡左色条 + 标题 + 元行 + 状态徽标
- 右侧：详情抽屉，AI 摘要卡 + 思考过程 + 操作 + 通知日志 + 处理记录

- [ ] **Step 2.1：完全重写 template，套用 dashboard 已建立的 panel-block / eyebrow / hero 设计语言**
- [ ] **Step 2.2：保留所有现有逻辑（loadAll / openDetail / handleAction / triggerCmd / forceStory）**
- [ ] **Step 2.3：把 AI 摘要卡 + ThinkingPanel 嵌入详情区**
- [ ] **Step 2.4：build 验证 + 浏览器目检**
- [ ] **Step 2.5：commit**

---

## Task 3: 重做 IncidentsView

**Files:**
- Modify: `frontend/src/views/IncidentsView.vue`

视觉要点：
- 故障组卡片：左色条（topLevel）+ 对象名 + 持续时长 + 事件数大数字
- 内嵌时间轴：按事件时间顺序，节点 + 等宽时间戳
- 顶部状态过滤 chip 组

- [ ] **Step 3.1：完全重写 template**
- [ ] **Step 3.2：build + 目检**
- [ ] **Step 3.3：commit**

---

## Task 4: 重做 RulesView + NlRuleDialog

**Files:**
- Modify: `frontend/src/views/RulesView.vue`
- Modify: `frontend/src/components/alert/NlRuleDialog.vue`

视觉要点：
- 顶部 hero：规则统计 + 强化的 AI 一句话建规则横幅（带 shimmer 动画 + 箭头）
- 规则卡片：折叠条件公式 + 级别色条 + 启停 toggle + 操作行
- NL 对话框：把 prompt 输入做成"终端式"输入框，等宽字 + 闪烁光标

- [ ] **Step 4.1：重写 RulesView template**
- [ ] **Step 4.2：重写 NlRuleDialog template**
- [ ] **Step 4.3：build + 目检（NL 流程：填提示 → 等待 → 应用到表单）**
- [ ] **Step 4.4：commit**

---

## Task 5: 重做 ObjectsView

**Files:**
- Modify: `frontend/src/views/ObjectsView.vue`

视觉要点：
- 顶部 hero：4 类对象统计大数字
- 对象网格：每张卡片显示对象名 + 类型 icon + 状态点 + 标签 chips
- 类型 chip 切换条

- [ ] **Step 5.1：重写 template**
- [ ] **Step 5.2：build + 目检**
- [ ] **Step 5.3：commit**

---

## Task 6: 重做 ChannelsView

**Files:**
- Modify: `frontend/src/views/ChannelsView.vue`

视觉要点：
- 顶部 hero：3 渠道统计 + 今日发送成功率
- 渠道卡片：渠道 icon + 名称 + 类型徽标 + 最近发送状态
- 测试发送 dialog：模板化布局

- [ ] **Step 6.1：重写 template**
- [ ] **Step 6.2：build + 目检**
- [ ] **Step 6.3：commit**

---

## Task 7: 重做 SettingsView + AiSummaryCard + ThinkingPanel + CommandPalette

**Files:**
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/components/alert/AiSummaryCard.vue`
- Modify: `frontend/src/components/ai/ThinkingPanel.vue`
- Modify: `frontend/src/components/command/CommandPalette.vue`

视觉要点：
- SettingsView：LLM 配置卡片，每张卡左侧大模型名（display 字体）+ 右侧 base/key/model
- 测试 dialog：成功/失败 banner + 模型回复块 + 思考过程
- AiSummaryCard：四段水平时间轴或 grid，每段一个图标 + 标题 + 内容
- ThinkingPanel：保留紫色描边但配色调整为 cyan + 微脉动
- CommandPalette：终端风格弹窗，等宽输入 + 闪烁光标

- [ ] **Step 7.1：重写 SettingsView**
- [ ] **Step 7.2：重写 AiSummaryCard**
- [ ] **Step 7.3：调整 ThinkingPanel 与新主题对齐**
- [ ] **Step 7.4：重写 CommandPalette**
- [ ] **Step 7.5：build + 目检**
- [ ] **Step 7.6：commit**

---

## Task 8: 全局体验打磨与验收

- [ ] **Step 8.1：所有页面在 dark + light 两个主题下浏览一遍，截图对比，修补不一致的颜色**
- [ ] **Step 8.2：清理旧的、不再使用的样式（PageHeader.vue / StatCard.vue 旧实现已被新设计取代）**
- [ ] **Step 8.3：端到端体验：seed 演示数据 → 启动故事模式 → 等待告警 → 看 AI 摘要流式生成 → ⌘K 命令面板**
- [ ] **Step 8.4：用 Lighthouse / 浏览器 DevTools 看动画性能不卡（FPS 60）**
- [ ] **Step 8.5：commit + push 到 GitHub**

---

## 设计语言备忘（贯穿所有页面）

**配色 (dark)**
- 背景：`#07080C` → `#0E1018` → `#151823` → `#1C2030`
- 文本：`#F4F5FB` / `#B5B9CC` / `#6E7385` / `#444859`
- 主色：electric cyan `#7DD3FC`
- 状态：emerald `#34D399` / amber `#FBBF24` / rose `#F87171` / pink-rose `#FB7185`(critical)

**配色 (light)** ← 待 Task 1 实现
- 背景：`#FAFAF9` → `#FFFFFF` → `#F4F5F7` → `#EAECEF`
- 文本：`#0A0A0F` / `#3F4254` / `#6E7385` / `#9AA0B0`
- 主色：保留 cyan，但加深为 `#0EA5E9`
- 状态色加深保持对比度

**字体**
- display: Space Grotesk
- mono: JetBrains Mono
- sans: Noto Sans SC

**关键 motif**
- `.eyebrow` mono 全大写小标签 + 1px 短刻度
- 大数字 hero 用 Space Grotesk 96-38px
- panel 顶部 1px inset 高光
- 微动画：呼吸点、淡入、shimmer

---

## 自检 (Self-Review)

1. **覆盖**：8 个 task 覆盖所有 7 个 view + 4 个组件 + 主题切换。✅
2. **占位符**：每步给具体行为，没有 "TODO/TBD"。✅
3. **类型一致**：theme store 用 `ThemeMode` 类型，所有 view 复用 dashboard 的 css 变量。✅
