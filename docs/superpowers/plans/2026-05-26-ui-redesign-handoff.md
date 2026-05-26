# UI 重设计接力清单

> 计划原文：`docs/superpowers/plans/2026-05-26-ui-redesign-and-theme.md`
> **状态：全部完成 ✅（Task 1-8 已 push 到 GitHub `main`）**

## 完成清单

- [x] **Task 1: 主题切换** — `dark/light/system` 三态。CSS token 拆分、Pinia store、ThemeSwitcher、防闪白脚本
- [x] **Task 2: EventsView** — Hero + 三栏（队列侧栏 / 事件流 / 详情）+ AI 摘要 / 思考面板嵌入
- [x] **Task 3: IncidentsView** — Hero + 故障组卡片 + 事件时间轴
- [x] **Task 4: RulesView + NlRuleDialog** — Hero + AI 终端横幅（shimmer + 打字机）+ 规则卡 + toggle 开关；NL 对话框终端外壳（红黄绿点 + 等宽输入 + 闪烁光标）
- [x] **Task 5: ObjectsView** — Hero + 类型分布卡 + 对象网格
- [x] **Task 6: ChannelsView** — Hero + 统计带 + 渠道卡 + 修硬编码 #0F172A
- [x] **Task 7: SettingsView + AiSummaryCard + ThinkingPanel** — LLM 卡片重排 / 摘要四段时间轴 / 思考面板紫→cyan
- [x] **Task 8: CommandPalette 终端化 + 全局打磨**
  - CommandPalette 重写：终端命令行风（红黄绿点 / READY 灯 / › 提示符 / 闪烁光标）
  - DashboardView + TrendChart 的 echarts 颜色改读 CSS token，主题切换时自动重渲染
  - RuleConditionEditor / DemoBanner / App.vue 的旧 token 与硬编码颜色全部清理
  - 删除无人引用的 `PageHeader.vue` `StatCard.vue`

## 设计 token 速查（继续维护用）

```css
/* dark / light 都从 CSS 变量取，不要硬编码 */
背景：var(--bg-base) / var(--bg-elev-1) / var(--bg-elev-2) / var(--bg-elev-3)
描边：var(--line) / var(--line-strong)
文本：var(--text-primary) / --text-secondary / --text-muted / --text-faint
主色：var(--accent) + var(--accent-soft) + var(--accent-line)
状态：var(--ok) / --warn / --danger / --critical （+ -soft 变体）
字体：var(--font-display) / --font-sans / --font-mono
圆角：var(--radius-sm/md/lg)
内阴影：var(--inset)
```

## 标志性构件（已统一使用，新增页面继续复用）

```html
<!-- Hero 区结构 -->
<section class="hero">
  <div class="hero-left">
    <div class="hero-eyebrow">
      <span class="eyebrow">SECTION NAME</span>
      <span class="dot-anim" />
      <span class="hero-time">实时信息</span>
    </div>
    <div class="hero-headline">
      <span class="hero-num">{{ N }}</span>
      <div class="hero-words">
        <div class="hero-line-1">主标题</div>
        <div class="hero-line-2">副信息</div>
      </div>
    </div>
  </div>
  <div class="hero-right">
    <button class="hero-action ghost">辅按钮</button>
    <button class="hero-action primary">主按钮</button>
  </div>
</section>

<!-- Eyebrow 标签 -->
<div class="eyebrow">SECTION TITLE</div>

<!-- 卡片 -->
<article class="event-card / incident-card / rule-card">
  <span class="lv-bar / lv-strip" :style="{ background: ... }" />
  <div class="body">...</div>
</article>

<!-- 终端外壳（Rule AI 横幅 / NlRuleDialog / CommandPalette 共用模式）-->
<div class="terminal-shell">
  <div class="term-head">
    <span class="term-dot r" /><span class="term-dot y" /><span class="term-dot g" />
    <span class="term-name">aiops:xxx</span>
    <span class="term-status"><span class="dot-anim" />READY</span>
  </div>
  <div class="term-body">…</div>
</div>
```

## echarts 主题对接（继续新增图表时参考）

DashboardView / TrendChart 已采用 token-aware 模式：

```ts
import { useThemeStore } from '@/stores/theme'
const theme = useThemeStore()

function readToken(name: string, fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

// setOption 时所有颜色用 readToken('--accent', '#7DD3FC') 等
// watch theme.isDark，requestAnimationFrame 后重渲染
```

## 当前服务状态

- 后端：http://localhost:8090/api （H2 dev profile）
- 前端：vite dev server，端口 5173-5177 之一
- LLM：智谱 GLM-5.1，已配置且测试连通成功
- 演示数据：seed API 可用（7 对象 / 3 渠道 / 6 规则）

## 验证

- `npm run build`：`vue-tsc --noEmit && vite build` 通过
- 所有页面在 dark/light 两个主题下颜色一致，无硬编码深色错色
- 删除的旧组件 `PageHeader.vue` `StatCard.vue` 已无引用
