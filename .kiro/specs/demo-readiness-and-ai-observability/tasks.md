# Implementation Plan

> Spec: demo-readiness-and-ai-observability
> Requirements: `requirements.md`
> Design: `design.md`

## Overview

按交付增量分组：A（演示数据）、B（加载态/异常）、C（AI 统计页）、D（验收）。每个任务对应 design 中的一节具体改动，可独立 commit。三个增量相互独立，可并行；增量内部按依赖顺序执行。

## Task Dependency Graph

```
A1 ──► A2 ──┐
            ├──► A4
A1 ──► A3 ──┘

B1 ──────────────────┐
B2 ──► B3 ──┐         │
            ├─────────┤──► B6
B4 ─────────┤         │
B5 ─────────┘         │
                      │
C1 ──► C2 ──► C3 ──► C4 ──► C5 ──► C6 ──┐
                                  └──► C7 ──┘──► C8

A4 ──┐
B6 ──┼──► D1
C8 ──┘
```

依赖说明：
- A 内：A1 先建骨架，A2 接通 seed + 历史事件，A3 写 metric_sample，A4 端到端；A2/A3 同依 A1 但相互独立可并行
- B 内：B1 独立，B2 是 B3 的前置，B4/B5 各自独立，B6 等所有 B 子任务
- C 内：从 C1 到 C8 严格线性，C5 是 C6 + C7 的共同前置（C7 也用到路由可视化时机）
- D1 必须等三个增量收尾（A4 / B6 / C8）

并行执行波（wave-based）：

```json
{
  "waves": [
    { "wave": 1, "tasks": ["A1", "B1", "B2", "B4", "B5", "C1"] },
    { "wave": 2, "tasks": ["A2", "A3", "B3", "C2"] },
    { "wave": 3, "tasks": ["A4", "C3"] },
    { "wave": 4, "tasks": ["C4"] },
    { "wave": 5, "tasks": ["C5"] },
    { "wave": 6, "tasks": ["C6", "C7"] },
    { "wave": 7, "tasks": ["B6", "C8"] },
    { "wave": 8, "tasks": ["D1"] }
  ]
}
```

## Tasks

按交付增量分组：A（演示数据）、B（加载态/异常）、C（AI 统计页）。每个任务对应 design 中的一节具体改动，可独立 commit。

依赖关系（用粗体 _Depends on_ 表达）：
- B / C 相互独立，可并行
- A 与 B / C 相互独立
- C2-C6 内有前后依赖（schema → entity → service → controller → API → view）

---

## A. 演示数据完善

### A1. DemoDataService 历史回填基础设施

- [ ] A1. 在 DemoDataService 内部新增私有方法，实现历史 AlertEvent / 预生成摘要 / MetricSample 三类回填（仅写代码骨架与签名，先不接到 seed）
  - **File**: `backend/src/main/java/com/aiops/alert/service/support/DemoDataService.java`
  - 新增 `private void seedHistorical(List<MonitorObject> objects, List<AlertRule> rules)`
  - 新增 `private void backfillEvent(AlertRule rule, MonitorObject object, String currentValue, String reason, LocalDateTime triggeredAt, String eventStatus, String preGeneratedSummaryJson)` —— 直接 `eventMapper.insert`，绕过 `triggerEvent`
  - 新增 `private void backfillMetricSamples(Long objectId, String metricCode, String unit, double baseValue, double sigma, LocalDateTime now)` —— 写 7 天 ~100 条 metric_sample
  - 新增 `private static final List<String> PREGENERATED_SUMMARIES`：3 条预置 JSON 字符串（按 design 中模板）
  - 暂不调用，确保编译通过
  - _Validates Requirements: 1.1, 2.1, 3.1_

### A2. 接通 seed() + 时间分布与状态混合策略

- [ ] A2. 把 A1 的方法接到 `seed()` 主流程，落实 design 中的"4×RECOVERED + 3×CLOSED + 3×CONFIRMED + 2×PENDING / 4×CRITICAL + 5×SERIOUS + 3×NORMAL / ≥3 种对象类型"分布
  - **File**: `backend/src/main/java/com/aiops/alert/service/support/DemoDataService.java`
  - 在 `seed()` 末尾、当前的 6 条 `eventService.triggerEvent(...)` 之前调用 `seedHistorical(objects, allRules)`
  - 当前的 6 条 `triggerEvent` 保留（这些是"今日 PENDING + AI 调用"演示用），但由于 AI 摘要异步、保留 1 条 PENDING 用于现场演示（满足 Req 2.4）
  - 实现 `seedHistorical`：按固定时间表（每日 08:30 / 13:15 / 19:42 三选一）分配 12 条事件到 day-1 ~ day-7
  - 选 3 条 RECOVERED 历史事件，把 PREGENERATED_SUMMARIES 中第 i 条作为 `aiSummary`、`aiSummaryStatus='SUCCESS'`、`aiReasoning='基于历史相似事件特征生成'`（满足 Req 2.1, 2.2, 2.3）
  - SQL 抽查：seed 后 `select count(*) from alert_event` ≥ 18（12 历史 + 6 当下）
  - _Validates Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4_
  - **Depends on**: A1

### A3. MetricSample 7 天分位数据回填

- [ ] A3. 实现 `backfillMetricSamples`，对所有数值条件型规则关联的 (object, metric) 维度写 7 天 ~100 条样本
  - **File**: `backend/src/main/java/com/aiops/alert/service/support/DemoDataService.java`
  - 遍历 6 条规则的 conditions，跳过 state 类型（如 `job_status`）
  - 对 (object_id, metric_code) 维度按 `LocalDateTime` 从 `now - 7day` 到 `now`，每天 14 个时刻（每 1.7h 一个）写一条 `metric_sample`
  - `numeric_value` 用 `baseValue + sigma * sin(idx * 0.4) + ThreadLocalRandom.nextDouble(-sigma/3, sigma/3)`，确保 P95 - P50 > 0
  - 在 `seed()` 中追加调用：`for (rule : rules) { for (cond : rule.conditions) { for (objectId : ruleObjectIds) backfillMetricSamples(...) } }`
  - SQL 抽查：`select count(*) from metric_sample where object_id=X and metric_code=Y` ≥ 100
  - _Validates Requirements: 3.1, 3.2, 3.3_
  - **Depends on**: A1

### A4. A 模块端到端验证

- [ ] A4. 跑通 `clean → seed`，前后端联调验证总览大屏 7 日趋势曲线显著、详情页两条 SUCCESS 摘要可见、阈值推荐返回 HISTORY 来源
  - 重启后端 H2 dev → 调 `POST /api/demo/clean` → `POST /api/demo/seed`
  - 浏览器查 `/dashboard`：7 日趋势图四条曲线均有起伏（不再是单日柱）
  - 点开任意 RECOVERED 历史事件详情：AiSummaryCard 直接显示 4 段内容（无 PENDING 等待）
  - 在 RulesView 编辑任一数值规则 → AI 阈值推荐弹窗 → 检查 `source` 字段是 `HISTORY`、`samples` ≥ 100
  - **手动 commit**：`feat(demo): seed 历史 7 天事件 + 预生成摘要 + 100+ metric_sample (Task A)`
  - _Validates Requirements: 1.*, 2.*, 3.*_
  - **Depends on**: A2, A3

---

## B. 加载态与异常处理

### B1. AiSummaryCard PENDING 打字机视觉

- [ ] B1. 在现有 AiSummaryCard 的 loading 块底部加"终端打字机"行（不重写已有 skeleton）
  - **File**: `frontend/src/components/alert/AiSummaryCard.vue`
  - 在 `<div v-if="loading || status === 'pending'" class="loading">` 内、现有 `.loading-text` 的位置替换为 `.thinking-line` 容器：含 `prompt-mark` "▸"、`thinking-text` 循环消息、`caret` 闪烁元素
  - script 内加 `thinkingMessages` 4 条中文 + `thinkingText` ref + `setInterval` 1.6s 切换；mounted 启动、unmounted 清理
  - css 复用已有 `--accent` / `--text-muted`，新增 `.caret` `.prompt-mark` `.thinking-text` 三个样式
  - 验证：保持 SUCCESS / FAILED 视觉不变；卡片根 DOM 节点 `.ai-summary-card` 在切换时引用不变（Property 5）
  - _Validates Requirements: 4.1, 4.2, 4.3, 4.4_

### B2. SkeletonList 通用组件

- [ ] B2. 新建通用骨架屏组件，复用 Element Plus `el-skeleton`
  - **File**: `frontend/src/components/common/SkeletonList.vue` (新建)
  - Props: `rows` (默认 6) / `variant` ('card' | 'row'，默认 'card')
  - 'card' variant：每行渲染左侧 3px 色条 + 右侧三段灰色占位（标题 / 元行 / 描述）
  - 'row' variant：紧凑型，单行 + 右侧大数字占位
  - 颜色：背景 `--bg-elev-1` + 边框 `--line` + 占位条 `linear-gradient(90deg, --bg-elev-2, --line-strong, --bg-elev-2)` 配 `@keyframes skel`（从 AiSummaryCard 提取或重新声明）
  - 不引入硬编码颜色
  - _Validates Requirements: 5.1, 5.2, 5.5_

### B3. 5 个 ListView 接入 SkeletonList

- [ ] B3. 在 EventsView / IncidentsView / RulesView / ObjectsView / ChannelsView 五个视图首屏接入 SkeletonList
  - **Files**:
    - `frontend/src/views/EventsView.vue`
    - `frontend/src/views/IncidentsView.vue`
    - `frontend/src/views/RulesView.vue`
    - `frontend/src/views/ObjectsView.vue`
    - `frontend/src/views/ChannelsView.vue`
  - 在每个 ListView 的列表区前加 `<SkeletonList v-if="loading && !list.length" :rows="6" :variant="..." />`
  - 现有 `v-loading` 保留，但首屏条件下用 `v-if` 屏蔽其遮罩；增量刷新（如点过滤）保留 `v-loading` spinner
  - 对应五个 view 的列表 ref 名：`events` / `incidents` / `list`(rules) / `list`(objects) / `list`(channels) —— 按各自实际命名
  - 空列表时 SkeletonList 退出，已有空状态 `<el-empty>` 仍正确显示
  - _Validates Requirements: 5.1, 5.2, 5.3, 5.4_
  - **Depends on**: B2

### B4. NetworkBanner + useHttpHealth

- [ ] B4. 新建 NetworkBanner 组件 + useHttpHealth composable + 拦截器 hook，挂到 App.vue
  - **Files**:
    - `frontend/src/composables/useHttpHealth.ts` (新建)
    - `frontend/src/components/layout/NetworkBanner.vue` (新建)
    - `frontend/src/api/http.ts` (修改：error 拦截器调 `useHttpHealth().reportFailure()`)
    - `frontend/src/composables/useSse.ts` (修改：暴露 `reconnect()` 方法)
    - `frontend/src/App.vue` (修改：在 `<main>` 前挂 `<NetworkBanner>`，并把 sseConnected ref 透传)
  - useHttpHealth：单例 store 风格（`ref` 模块顶层），5 分钟滚动窗口 + 阈值 3
  - NetworkBanner: `position: sticky; top: 0`，进出动画 `transition.banner-slide`，禁止覆盖正文
  - 颜色：默认 `--warn-soft` + `--warn`（SSE 断开），高频 5xx 升级 `--danger-soft` + `--danger`
  - 重连按钮 disabled 状态（reconnecting 中）
  - 30s 计时器：`watch(() => props.sseConnected)` 起停 timer
  - 恢复后 setTimeout 3s 隐藏
  - _Validates Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

### B5. ErrorPage 404 + 500

- [ ] B5. 新建 ErrorPage 组件 + 接入 router 404 兜底 + 在所有页面级 view 处理 5xx
  - **Files**:
    - `frontend/src/views/ErrorPage.vue` (新建)
    - `frontend/src/router/index.ts` (修改：尾部加 `/:pathMatch(.*)*` 路由)
    - `frontend/src/views/DashboardView.vue` (修改：`error500` ref + `<ErrorPage v-if="error500">` 包裹)
    - `frontend/src/views/EventsView.vue` (同上)
    - `frontend/src/views/IncidentsView.vue` (同上)
    - `frontend/src/views/RulesView.vue` (同上)
    - `frontend/src/views/ObjectsView.vue` (同上)
    - `frontend/src/views/ChannelsView.vue` (同上)
    - `frontend/src/views/SettingsView.vue` (同上)
  - ErrorPage Props: `variant: '404' | '500'` / `message?` / `onRetry?`
  - 视觉：套用 terminal-shell（红黄绿点）+ 大数字（96px display 字体）+ 说明 + 按钮
  - 颜色：404 主色 `--accent`，500 主色 `--critical`
  - 各 view 的 `loadAll()` catch 块：`if (e?.response?.status >= 500) error500.value = { retry: loadAll }`
  - 验证 chrome 保留：因 ErrorPage 是 RouterView 内部组件，App.vue 的 sidebar / header 自然在外层
  - _Validates Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

### B6. B 模块端到端验证

- [ ] B6. 跑通 `npm run build`，浏览器手动验证 PENDING 视觉、骨架屏、断网 banner、404/500 页面
  - 后端 `mvn spring-boot:run` 跑起来 → 前端 `npm run dev`
  - 手动触发演示告警 → 立即点开详情 → 看 PENDING 打字机循环；等 LLM 返回 → 切到 SUCCESS 四段卡
  - F12 清缓存 → 5 个 ListView 首屏均看到 SkeletonList
  - 后端关停 → 30 秒后 NetworkBanner 出现；启动后 3 秒消失
  - 浏览器输入 `/foo-bar` → 404 页 + 跳 dashboard 按钮
  - 切换到 light 主题再走一遍：所有视觉色彩正确
  - **手动 commit**：`feat(ui): 加载态打磨 + 错误页 + 网络异常 banner (Task B)`
  - _Validates Requirements: 4.*, 5.*, 6.*, 7.*, 16.1, 16.2, 16.4_
  - **Depends on**: B1, B3, B4, B5

---

## C. AI 调用统计页

### C1. Schema 升级：LlmModelConfig 单价列

- [ ] C1. 给 llm_model_config 加 `prompt_price_per_1k` / `completion_price_per_1k` 两列（H2 dev + MySQL prod 双脚本）
  - **Files**:
    - `backend/src/main/resources/schema-h2-upgrade.sql` (追加 2 条 idempotent ALTER)
    - `backend/src/main/resources/schema-h2.sql` (`create table llm_model_config` 块加两列)
    - `docs/schema.sql` (MySQL 文档同步)
    - `docs/schema-upgrade.sql` (MySQL 增量脚本，沿用已有 `information_schema.columns` 探测模式)
    - `backend/src/main/java/com/aiops/alert/entity/LlmModelConfig.java` (加 `BigDecimal promptPricePer1k` / `completionPricePer1k`)
  - 列类型 `decimal(10,4)`，可空
  - H2 增量脚本必须满足：旧库执行后无报错、行数不变、列值为 NULL（Property 11）
  - _Validates Requirements: 12.1, 14.1, 14.2, 14.3, 14.4, 17.4_

### C2. AiCallStatsService + DTO 套件

- [ ] C2. 实现后端聚合服务，按 design 模式照搬 DashboardService 的 Java 端聚合
  - **Files**:
    - `backend/src/main/java/com/aiops/alert/service/ai/AiCallStatsService.java` (新建)
    - `backend/src/main/java/com/aiops/alert/dto/AiStatsOverviewResponse.java` (新建，含 `SceneStat` `TrendItem` 内部类)
    - `backend/src/main/java/com/aiops/alert/dto/AiCallLogResponse.java` (新建)
    - `backend/src/main/java/com/aiops/alert/dto/AiCallLogQuery.java` (新建)
    - `backend/src/main/java/com/aiops/alert/dto/PageResult.java` (新建：泛型 page wrapper)
  - 方法：`loadOverview()` / `slowTop(int days, int limit)` / `page(AiCallLogQuery q)` / `get(Long id)`
  - 实现要点：
    - `normalizeScene(String s)`：白名单外归 `OTHER`
    - `calcCost(AiCallLog log, Map<Long, LlmModelConfig> priceIndex)`：单价 / model_config 缺失返回 `BigDecimal.ZERO`，`HALF_UP` 4 位小数（满足 Property 8）
    - hero 三数字：今日 / 昨日两窗口 selectList + Java 计算
    - 7 日趋势：按 `LocalDate` groupingBy.counting，缺失日填 0L
    - sceneDistribution: 今日数据按归一 scene 聚合，输出 SceneStat（callCount + tokenTotal + tokenPercent）
  - 列表接口（`page` + `slowTop`）映射 DTO 时 **不**填 `requestPayload` / `responsePayload` / `reasoningContent`（Property 12）
  - 详情接口（`get`）填全部字段
  - _Validates Requirements: 8.1, 8.2, 8.3, 8.5, 9.1, 9.2, 9.4, 10.1, 10.4, 11.1, 11.2, 11.5, 12.2, 12.3, 12.4, 12.5, 12.6, 13.1, 13.2_
  - **Depends on**: C1

### C3. AiCallStatsController + REST 端点

- [ ] C3. 实现 4 个 REST 端点
  - **File**: `backend/src/main/java/com/aiops/alert/controller/AiCallStatsController.java` (新建)
  - 路径：
    - `GET /api/ai-stats/overview` → `AiStatsOverviewResponse`
    - `GET /api/ai-stats/slow?days=7&limit=10` → `List<AiCallLogResponse>`
    - `GET /api/ai-stats/logs?scene=&modelName=&status=&page=1&size=20` → `PageResult<AiCallLogResponse>`
    - `GET /api/ai-stats/logs/{id}` → `AiCallLogResponse`
  - 默认值：`days=7` / `limit=10` / `page=1` / `size=20`
  - `size` 校验：`@Min(1) @Max(100)`
  - 不存在时抛 `BizException("AI 调用流水不存在")`
  - 用 Postman / curl 验证四端点返回格式
  - _Validates Requirements: 8.1, 9.1, 10.1, 11.1, 13.1_
  - **Depends on**: C2

### C4. 前端 API + 类型定义

- [ ] C4. 实现 `api/aiStats.ts` 与配套 TS 接口定义
  - **File**: `frontend/src/api/aiStats.ts` (新建)
  - 镜像后端 DTO 定义 5 个 interface：`AiStatsOverview` / `SceneStat` / `TrendItem` / `AiCallLogItem` / `AiCallLogPage`
  - 4 个 API 函数：`getAiStatsOverview()` / `listSlowAiCalls(params)` / `listAiCallLogs(params)` / `getAiCallLog(id)`
  - 走已有 `http.ts`，所以错误自动有 ElMessage
  - _Validates Requirements: 8.1, 9.1, 10.1, 11.1, 13.1_
  - **Depends on**: C3

### C5. AiStatsView Hero + 双图表

- [ ] C5. 实现 AiStatsView 顶部 Hero 区 + 场景分布环形图 + 7 日趋势折线
  - **File**: `frontend/src/views/AiStatsView.vue` (新建)
  - 整体结构按 design 划分四区：Hero / chart-row / row-2 / log-list
  - 本任务先实现前两区
  - Hero：3 个并列指标卡（今日调用 / 今日 token / 今日成功率），每个卡片下显示 `compareDelta` 环比文本与方向指示
  - 颜色：`+` `--ok`，`-` `--critical`，`=` `--text-muted`；空数据时不渲染方向指示（Req 8.5）
  - 复用 DashboardView 的 `.hero` / `.hero-eyebrow` / `.hero-num` 样式骨架（拷贝过来或抽到 global.css 共享）
  - chart-row：两个 `.panel-block`，左右各放一个 echarts 容器
  - 实现 `readToken(name, fallback)`、`renderSceneRing()`、`renderTrendLine()`
  - 在 `mounted` 调用 `loadAll()` → 并发拉 overview → renderAll
  - `watch(theme.isDark, () => requestAnimationFrame(renderAll))`
  - 颜色映射：scene → `[--accent, --warn, --ok, --critical, --text-muted]` 5 色
  - tooltip / axisLabel / legend 文本色全部走 token
  - _Validates Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 9.1, 9.2, 9.3, 9.4, 10.1, 10.2, 10.3, 10.4, 16.1, 16.2, 16.3_
  - **Depends on**: C4

### C6. AiStatsView 成本卡 + 慢调用 + 调用流水 + 抽屉

- [ ] C6. 完成 AiStatsView 剩余三区
  - **File**: `frontend/src/views/AiStatsView.vue`
  - 成本卡 (row-2 左)：今日 ¥ / 本月 ¥ 两个数字（保留 2 位小数，前置 `¥`）；用 `.hero-num` 风格但小一号（48px）
  - 慢调用 Top 10 (row-2 右)：`<table>` + 行点击展开 `<tr v-if="expanded === log.id">` 显示 prompt+response（`<pre>` + `--font-mono`，背景 `--bg-elev-2`）
  - 调用流水：3 个 `el-select` 筛选（scene / modelName / status，options 用枚举 + 后端 distinct）+ `el-pagination` 底部
  - 流水未返回时表格主体显示 `<SkeletonList :rows="8" variant="row" />`
  - 行点击 → `<el-drawer>` 显示 4 字段（requestPayload / responsePayload / reasoningContent / errorMessage）
  - 流水接口 5xx：仅显示 `<el-empty>` + ElMessage（不升级到 ErrorPage 500，Req 7.2 限页面级首屏）
  - _Validates Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 12.5, 12.6, 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_
  - **Depends on**: C5, B2

### C7. 路由 + 侧栏菜单

- [ ] C7. 注册 `/ai-stats` 路由 + 在侧栏图标映射加 `Activity`
  - **Files**:
    - `frontend/src/router/index.ts` (修改：在 settings 之后加路由项；并在末尾的 404 catch-all 之前)
    - `frontend/src/components/layout/AppSidebar.vue` (修改：`iconMap` 加 `Activity`)
  - 路由 meta: `{ title: 'AI 调用统计', icon: 'Activity' }`
  - AppSidebar 自动从 routes 派生 items，因此添加路由后菜单自动出现
  - 验证：`document.title` 在 `/ai-stats` 路径下包含 `'AI 调用统计 · AIOps Alert'`
  - 验证：选中态高亮（`router-link active-class="active"` 已有）
  - _Validates Requirements: 15.1, 15.2, 15.3, 15.4_
  - **Depends on**: C5

### C8. C 模块端到端验证

- [ ] C8. 后端 + 前端联调，跑通 AiStatsView 全功能
  - 后端 `mvn spring-boot:run`（确保 schema-h2-upgrade.sql 跑过 → 列存在）
  - 给一条 LlmModelConfig 设定单价：`update llm_model_config set prompt_price_per_1k=0.001, completion_price_per_1k=0.002 where is_default=1`
  - 触发若干 AI 调用：seed 演示数据 → AI 一句话建规则 → AI 摘要
  - `GET /api/ai-stats/overview` 返回三数字非零、scene 分布完整、7 日趋势 7 个点
  - 浏览器 `/ai-stats`：Hero 数字与环比一致；两图表色彩跟随主题；慢调用展开 prompt/response；成本卡 ¥ 数字正确；流水筛选+分页 ok；点行→抽屉 4 字段
  - 切换 light 主题：echarts 重渲染、所有面板色彩正确
  - 侧栏菜单"AI 调用统计"出现 + 选中态正确
  - **手动 commit**：`feat(ai-stats): AI 调用统计页 + 后端聚合 + 单价配置 (Task C)`
  - _Validates Requirements: 8.*, 9.*, 10.*, 11.*, 12.*, 13.*, 14.*, 15.*, 16.*_
  - **Depends on**: C6, C7

---

## D. 验收

### D1. 全 spec 验收 + 文档更新

- [ ] D1. 跑完整 `npm run build` + 端到端演示流程，更新接力文档与计划文档
  - `cd frontend && npm run build` → 0 error / 0 warning（vue-tsc + vite build）
  - 后端 `cd backend && ./mvnw.cmd compile` 通过
  - 端到端：`POST /api/demo/clean → seed → 点 RulesView 启动故事模式 → 30~60s 自动告警出现 → 详情页 PENDING 打字机 → SUCCESS 四段 → ⌘K 命令面板 → /ai-stats 看到刚写的 AiCallLog 计入今日 hero`
  - dark / light 双主题各跑一遍
  - 编辑 `docs/开发计划.md`：把 P0-3 / P0-4 / P1-6 三项标记为已完成
  - 编辑 `docs/superpowers/plans/2026-05-26-ui-redesign-handoff.md`：在底部追加一段"演示就绪与 AI 可观测 spec 已完成"
  - **手动 commit**：`docs: P0-3/P0-4/P1-6 完成，更新开发计划与接力清单`
  - 推送：`git push origin main`
  - _Validates Requirements: 17.1, 17.2, 17.3, 17.4_
  - **Depends on**: A4, B6, C8


## Notes

- **不引入新测试框架**：本仓库无 vitest / JUnit 基线，所有验证用手动 / SQL 抽查 / build 通过来验证（详见 design `Testing Strategy`）。
- **每个增量独立 commit**：A4 / B6 / C8 三个验证任务结尾分别 commit + push，方便回滚。最后 D1 一并 push。
- **Token-only 强约束**：A 模块不涉及前端样式所以无关；B/C 模块每个新建组件提交前用 `grep -E '#[0-9a-fA-F]{6}|rgba?\(' src/views/AiStatsView.vue ...` 自查零硬编码颜色（注释除外）。
- **schema-h2-upgrade.sql 幂等**：第二次启动后端不应重复加列报错，H2 的 `add column if not exists` 自然支持。
- **不破坏 main 已 commit 视图**：SettingsView / AiSummaryCard SUCCESS 块 / CommandPalette 在 B / C 中均不被改动；A 与 B / C 完全互不依赖。
- **演示主路径不阻塞**：即便 C 模块没做完，A + B 也足以撑起 5 分钟演示；C 是加分项。
- **手动 commit 节奏**：每个 `*4` / `*6` / `*8` 验证任务通过后 commit。中间任务（A1-A3 / B1-B5 / C1-C7）不强制单独 commit，但建议每完成 2-3 个任务 squash 一次便于审阅。
