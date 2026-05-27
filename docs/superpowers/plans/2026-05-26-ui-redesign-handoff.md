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

## 后续 spec ✅ 已完成

### demo-readiness-and-ai-observability（演示就绪与 AI 可观测）

📍 spec 路径：`.kiro/specs/demo-readiness-and-ai-observability/`

**子模块 A · 演示数据完善**（P0-3）
- `DemoDataService.seedHistorical()` 回填 12 条 AlertEvent 分散到近 7 天，状态/级别/对象类型多维覆盖
- 3 条 RECOVERED 历史事件预生成 SUCCESS 状态 AI 摘要（直接写库不调 LLM）
- 至少 1 条 PENDING 残留供演示现场触发
- 每个数值条件型 (object_id, metric_code) 写 ~98 条 7 天 MetricSample，让阈值推荐返回 HISTORY 来源

**子模块 B · 加载态与异常处理**（P0-4）
- `AiSummaryCard` PENDING 状态新增终端打字机动画（4 段循环消息 + 闪烁光标），SUCCESS 切换时根 DOM 节点不变
- 新建 `SkeletonList.vue`（card / row 两种 variant），5 个 ListView 首屏接入
- 新建 `NetworkBanner.vue`：SSE 断开 ≥30s 或 5min 内 5xx ≥3 次显示，重连按钮，sticky 顶部不覆盖正文
- 新建 `useHttpHealth.ts`：5 分钟滚动窗口监控 5xx，单例 store
- 给 `useSse` 暴露 `reconnect()` 方法
- 新建 `ErrorPage.vue`：404 + 500 形态，套用 terminal-shell 视觉

**子模块 C · AI 调用统计页**（P1-6）
- `LlmModelConfig` 加 `prompt_price_per_1k` `completion_price_per_1k` 两列，支持 H2 + MySQL 双向 idempotent 升级
- 后端：`AiCallStatsService` + `AiCallStatsController`，4 个端点 (`/overview` `/slow` `/logs` `/logs/{id}`)
- 后端 DTO：`AiStatsOverviewResponse` + `AiCallLogResponse` + `AiCallLogQuery` + `PageResult<T>`
- 列表接口剥离 `requestPayload` / `responsePayload` / `reasoningContent` 三个大字段，仅详情接口返回
- 前端：`AiStatsView.vue`（Hero + KPI + 双图表 + 慢调用 + 流水 + 抽屉）
- echarts 全部走 token-aware 模式，主题切换时自动重渲染
- 路由 `/ai-stats` + 侧栏菜单"AI 调用统计"（lucide `Activity` 图标）

**验证**：
- 后端 `mvnw compile` 通过（JDK 17）
- 前端 `npm run build` 通过（28.99s，AiStatsView 16.73KB）
- 所有 diagnostics 干净

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
- LLM：已迁移为 DeepSeek V4 Flash/Pro 配置
- 演示数据：seed API 可用（7 对象 / 3 渠道 / 6 规则）

## 验证

- `npm run build`：`vue-tsc --noEmit && vite build` 通过
- 所有页面在 dark/light 两个主题下颜色一致，无硬编码深色错色
- 删除的旧组件 `PageHeader.vue` `StatCard.vue` 已无引用
