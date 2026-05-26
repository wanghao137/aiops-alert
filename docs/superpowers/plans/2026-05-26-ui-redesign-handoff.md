# UI 重设计接力清单

> 上一轮 context 接近上限，这是给下一轮 AI / 自己的接力交接文档。
> 计划原文：`docs/superpowers/plans/2026-05-26-ui-redesign-and-theme.md`

## 已完成（已 push 到 GitHub `main`）

- [x] **Task 1: 主题切换** — `dark/light/system` 三态。CSS token 拆分、Pinia store、ThemeSwitcher 组件、index.html 防闪白脚本。
- [x] **Dashboard 已重写**（这是设计样板，所有其他页面照抄）
- [x] **Task 2: EventsView 重写** — Hero + 三栏（状态侧栏/事件流/详情）+ 新事件卡 + AI 摘要/思考面板嵌入
- [x] **Task 3: IncidentsView 重写** — Hero + 故障组卡片 + 时间轴
- [x] **AppHeader / AppSidebar 已对齐** 到新设计语言
- [x] **bug 修：AppHeader 顶部黑边**（写死了 `rgba(7,8,12)`，已改成 `var(--bg-base)`）

## 待做（按顺序）

### Task 4: RulesView + NlRuleDialog 重写
- 文件：`frontend/src/views/RulesView.vue` + `frontend/src/components/alert/NlRuleDialog.vue`
- 视觉要点：
  - Hero（规则总数 + 启用 + 4 级别分布）
  - **强化 AI 横幅**：渐变描边 + shimmer 动画 + "终端式输入框" placeholder 动态打字效果
  - 规则卡片：左色条（级别）+ 规则编号 + 名称 + 触发公式（mono）+ 启停 toggle + 操作行
  - NlRuleDialog 输入区做成"终端命令行"风格：等宽字体 + `>` 提示符 + 闪烁光标
- 注意：NlRuleDialog 顶部已经接入 ThinkingPanel + understanding 卡，重写时保留

### Task 5: ObjectsView 重写
- 文件：`frontend/src/views/ObjectsView.vue`
- 视觉要点：
  - Hero（4 类对象）
  - 类型 chip 切换条
  - 对象网格：每张卡片含对象名 + 类型 icon（带类型色光晕）+ 状态点 + 标签 chips + 描述

### Task 6: ChannelsView 重写
- 文件：`frontend/src/views/ChannelsView.vue`
- 视觉要点：
  - Hero（3 渠道 + 今日发送 + 失败）
  - 渠道卡片：渠道 icon + 名称 + 类型 + 最近发送状态徽标
  - **修硬编码深色：`background: #0F172A` 改成 `var(--bg-elev-1)`**（line 762）
  - 测试发送 dialog：成功/失败 banner + 模型回显

### Task 7: SettingsView + AiSummaryCard + ThinkingPanel + CommandPalette 重写
- 文件：
  - `frontend/src/views/SettingsView.vue`
  - `frontend/src/components/alert/AiSummaryCard.vue`
  - `frontend/src/components/ai/ThinkingPanel.vue`
  - `frontend/src/components/command/CommandPalette.vue`
- 视觉要点：
  - SettingsView：LLM 卡片左侧大模型名（display 字体）+ 右侧 baseUrl/key/model
  - AiSummaryCard：4 段（what/impact/causes/actions）改成水平时间轴或 grid，每段独立色彩
  - ThinkingPanel：颜色从紫改 cyan，保留呼吸动画
  - **CommandPalette：硬编码 `linear-gradient(180deg, #1F2937, #111827)` 改 `var(--bg-elev-1) → var(--bg-elev-2)`**（line 219）
  - CommandPalette 输入做终端风格：等宽 + `›` 提示符

### Task 8: 全局打磨与验收
- 巡检所有硬编码深色（grep `#0F172A` `#1F2937` `#111827` `rgba(7, 8`）
- 浅色主题逐页过一遍，截图对比
- 删除旧组件：`frontend/src/components/common/PageHeader.vue` `StatCard.vue` 已不再被引用，可删
- 端到端：seed → 启动故事 → 自动告警 → AI 摘要 → ⌘K
- 最后 commit + push

## 关键设计 token（重要 — 所有 view 通用）

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

## 标志性构件（已在 dashboard / events / incidents 用，下一轮复用）

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
  <span class="lv-bar" :style="{ background: ... }" />
  <div class="body">...</div>
</article>
```

## 命令行接力提示（粘到下一个 session 开头）

> 我在执行 `docs/superpowers/plans/2026-05-26-ui-redesign-and-theme.md` 计划。
> 已完成 Task 1-3（主题切换 / Dashboard / Events / Incidents）。
> 接力清单见 `docs/superpowers/plans/2026-05-26-ui-redesign-handoff.md`。
> 设计语言：Editorial × Terminal，全部用 CSS 变量，禁止硬编码深色。
> 现在开始 Task 4：RulesView + NlRuleDialog 重写。

## 当前服务状态

- 后端：http://localhost:8090/api （H2 dev profile）
- 前端：vite dev server，端口 5173-5177 之一（最后一次是 5177）
- LLM：智谱 GLM-5.1，已配置且测试连通成功
- 演示数据：已 seed（7 对象 / 3 渠道 / 6 规则）

## 已知问题

- ChannelsView line 762: 硬编码 `background: #0F172A`，浅色主题下错色
- CommandPalette line 219: `linear-gradient(180deg, #1F2937, #111827)`，浅色主题下错色
- 旧组件 `components/common/PageHeader.vue` `StatCard.vue` 已无引用，可删
- backend AlertEvent.toResponse 在 `getAiReasoning()` 上仍 OK（已修过 schema-h2-upgrade.sql）
