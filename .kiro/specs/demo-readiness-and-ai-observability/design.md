# Design Document

> 子标题：演示就绪与 AI 可观测 (demo-readiness-and-ai-observability)

## Overview

本设计文档把 `requirements.md` 中 17 个 EARS 形式的需求，落实成具体的代码契约：DDL、DTO、API、组件结构、CSS token、echarts option、错误流和验证清单。

设计层面分三个交付增量，对应需求文档的三个子模块：

- **A 演示数据完善**：在已有 `DemoDataService.seed()` 之外新增 `seedHistorical()` 私有阶段。绕过 `AlertEventService.triggerEvent`（它会把 `first_triggered_at = now()` 并触发 SSE/LLM 异步链路），改用直接 mapper insert + 显式时间戳的 `BackfillHelper`。同步生成 `MetricSample` 7 天分位数据。
- **B 加载态与异常处理**：复用 `AiSummaryCard` 已有的 `loading skeleton`（已实现），仅补充 PENDING 状态的"AI 思考中"打字机文案与光标元素；新建 `SkeletonList.vue` 通用组件被五个 ListView 引用；新建 `NetworkBanner.vue` 挂在 `App.vue` 顶层，订阅 `useSse().connected` 与一个新的 `useHttpHealth` composable；新建 `ErrorPage.vue` 用于 404 与 500 形态。
- **C AI 调用统计页**：新增 `/api/ai-stats/*` 五个 REST 端点 → `AiCallStatsService` → 直接走 `AiCallLogMapper.selectList` + Java 端聚合（沿用 `DashboardService` 的成熟模式，避免方言差异）。前端新增 `AiStatsView.vue` + `api/aiStats.ts` + 路由项 + 侧栏菜单。`llm_model_config` 加两列单价。

设计严格遵守："Editorial × Terminal" 设计语言、token-only、token-aware echarts、dark/light 双主题验证、不破坏 main 已有视图。

## Steering Document Alignment

仓库 `.kiro/steering/` 暂无 steering 文档。本设计不引入新的项目约定。

## Code Reuse Analysis

可直接复用的现有模块：

| 模块 | 复用方式 |
|---|---|
| `DashboardService.load()` | `AiCallStatsService.load()` 拷贝其分组聚合 + `lowerKeys`/`toLong` 帮助方法风格 |
| `DashboardView.vue` 的 `readToken()` 模式 | `AiStatsView.vue` 全部 echarts 直接复用，TokenAwareEcharts 由 `theme.isDark` 驱动重渲染 |
| `AiSummaryCard.vue` 已有 loading skeleton | PENDING 状态视觉只补一段"AI 思考中…"等宽文案 + 闪烁光标，不重写卡片骨架 |
| `useSse().connected` ref | `NetworkBanner` 直接订阅；额外加 `useHttpHealth()` 监听 axios 5xx |
| `terminal-shell` 视觉模式（NlRuleDialog / CommandPalette / RulesView 横幅） | `ErrorPage.vue` 500 形态直接套用红黄绿点 + `term-name` |
| `el-skeleton`（Element Plus 已含） | `SkeletonList.vue` 用 `el-skeleton-item` 拼接，避免重新写动画 |
| `LlmClient` 写 `ai_call_log` 入口 | 不动；只读 |
| `MonitorObjectMapper` / `AlertRuleMapper` 等 | seed 历史时直接复用 |

需要新建（无既有可复用）：

- `AiCallStatsService` / `AiCallStatsController` / `AiStatsResponse` DTO 套件
- `LlmModelConfig.promptPricePer1k` / `completionPricePer1k` 字段
- `BackfillHelper`（演示数据历史生成的小工具，DemoDataService 内部静态方法即可，不必开新 class）
- `SkeletonList.vue` / `NetworkBanner.vue` / `ErrorPage.vue` / `AiStatsView.vue`
- `useHttpHealth` composable（一个 axios 拦截器 + 滚动窗口计数 store）
- `api/aiStats.ts`

## Architecture

```
┌─────────────────────────── Frontend ───────────────────────────┐
│  AppSidebar (+/ai-stats menu)                                   │
│  App.vue                                                        │
│   ├── NetworkBanner (顶部固定，监听 sse + httpHealth)             │
│   ├── RouterView                                                │
│   │    ├── DashboardView (现有)                                  │
│   │    ├── EventsView    (引入 SkeletonList)                     │
│   │    ├── IncidentsView (引入 SkeletonList)                     │
│   │    ├── RulesView     (引入 SkeletonList)                     │
│   │    ├── ObjectsView   (引入 SkeletonList)                     │
│   │    ├── ChannelsView  (引入 SkeletonList)                     │
│   │    ├── SettingsView  (现有)                                  │
│   │    ├── AiStatsView   (新增)                                  │
│   │    └── ErrorPage     (404 / 500)                             │
│   └── CommandPalette (现有)                                      │
└────────────────────────────────────────────────────────────────┘
                          │ HTTP /api/ai-stats/*
                          ▼
┌─────────────────────────── Backend ────────────────────────────┐
│  AiCallStatsController                                          │
│   └── AiCallStatsService                                        │
│        ├── AiCallLogMapper      (现有)                           │
│        └── LlmModelConfigMapper (现有)                           │
│  DemoDataController                                             │
│   └── DemoDataService.seed() — 增强：调用 seedHistoricalEvents() │
│       + seedHistoricalMetricSamples() + seedPregeneratedSummary()│
└────────────────────────────────────────────────────────────────┘
                          │ ALTER TABLE
                          ▼
┌─────────────────────────── DB ─────────────────────────────────┐
│  llm_model_config + prompt_price_per_1k DECIMAL(10,4)           │
│  llm_model_config + completion_price_per_1k DECIMAL(10,4)       │
└────────────────────────────────────────────────────────────────┘
```



## Components and Interfaces

### A1. DemoDataService 历史回填（满足 Req 1, 2, 3）

**位置**：`backend/src/main/java/com/aiops/alert/service/support/DemoDataService.java`（增强现有类，不新建）

**新增私有方法**：

```java
/** 在已有 7 类对象 / 6 条规则的基础上，回填近 7 天 AlertEvent + MetricSample。 */
private void seedHistorical(List<MonitorObject> objects, List<AlertRule> rules)

/** 直接 mapper.insert AlertEvent，绕过 triggerEvent，使用显式时间戳。 */
private void backfillEvent(AlertRule rule, MonitorObject object,
                           String currentValue, String reason,
                           LocalDateTime triggeredAt, String eventStatus,
                           String preGeneratedSummaryJson /* nullable */)

/** 为单个 (object, metricCode) 写 7 天 100+ 条 metric_sample。 */
private void backfillMetricSamples(Long objectId, String metricCode,
                                    String unit, double baseValue, double sigma)
```

**回填策略**（确定性，演示效果可控）：

- 最少生成 12 条 AlertEvent，按 `(today - 7) ~ (today - 1)` 7 个完整日历日 × 每日 1-3 条交错排布。
- 时间戳分布：每日内随机选 1-3 个时刻（08:30 / 13:15 / 19:42 等固定模板），让总览大屏 7 日趋势图四条曲线都有起伏。
- 状态混合：4 条 `RECOVERED`（带 `recovered_at`）、3 条 `CLOSED`（带 `closed_at` 与 `confirmed_at`）、3 条 `CONFIRMED`、2 条 `PENDING` —— 覆盖 4 状态的 ≥3 条要求 + ≥1 条 RECOVERED。
- 级别混合：4 条 `CRITICAL`、5 条 `SERIOUS`、3 条 `NORMAL` —— 覆盖 ≥2 种级别要求。
- 对象类型覆盖：12 条事件至少落在 SERVER / DATABASE / SYNC_JOB / PROCESS_JOB 中的 4 种（≥3 种要求）。
- 预生成摘要：选 3 条 `RECOVERED` 历史事件，把硬编码 JSON 串写入 `ai_summary` 字段、`ai_summary_status='SUCCESS'`、`ai_reasoning` 设为一段简短中文说明（演示思考过程）。
- 保留 ≥1 条 `ai_summary_status='PENDING'` 事件（最近一日的 PENDING 事件之一），用于演示现场 LLM 调用。

**MetricSample 回填**：对每个数值条件型规则（CPU / 内存 / 主从延迟 / 慢查询 / 加工时长）所关联的对象写 7 天 × ~14 条 = ~100 条样本；用 `baseValue + sigma * sin(t) + noise` 让分位数有差异（P50 != P95）。

**事务**：整个 `seed()` 已经在 `@Transactional` 包裹中，新增方法继承事务。

**预生成摘要 JSON 模板**（写到 `ai_summary` 字段）：

```json
{
  "what": "生产 MySQL 主库主从延迟超过 5 分钟，连续 3 次命中阈值",
  "impact": "读写分离场景下从库查询返回旧数据，订单查询和报表受影响",
  "causes": ["业务高峰期写入激增", "从库 IO 线程被慢 SQL 阻塞", "网络抖动导致 binlog 同步延迟"],
  "actions": ["短期：临时把读流量切回主库", "中期：排查从库慢 SQL 并加索引", "长期：评估迁移到 GTID 模式"]
}
```

### A2. SQL 无新增

子模块 A 不需要 schema 改动，使用现有 `alert_event` / `metric_sample` / `alert_incident` 表结构。

### B1. AiSummaryCard PENDING 占位（满足 Req 4）

**位置**：`frontend/src/components/alert/AiSummaryCard.vue`（增强现有组件，不重写）

**改动范围**：现有 `loading skeleton` 已有 4 段灰色占位卡 + "AI 正在分析这次告警…" 文案。**保留这部分不动**，仅扩展该 loading 块结构以体现"打字机/光标"动画，满足 Req 4.1 关于"动画指示元素"的具体可视化。

**新增 DOM 片段**（替换 `.loading-text` 为更明显的终端打字感）：

```vue
<div class="thinking-line">
  <span class="prompt-mark">▸</span>
  <span class="thinking-text">{{ thinkingText }}</span>
  <span class="caret" />
</div>
```

`thinkingText` 用一个 reactive `string`，4 段中文消息循环打字（"读取告警上下文…" → "比对历史相似事件…" → "分析根因路径…" → "生成处置建议…"），每条 1.6s，类似 RulesView 的打字机。状态切到 SUCCESS 时停止 timer。

**布局保持**：外层 `.ai-summary-card` 不变，PENDING / SUCCESS 切换时只替换内层 `.loading` ↔ `.content` 容器，满足 Req 4.3"不引发布局抖动"。

**Token 检查**：现有样式已经全部用 token；新加的 `.thinking-line` 复用 `--accent` / `--text-muted` 即可。

### B2. SkeletonList 通用组件（满足 Req 5）

**位置**：`frontend/src/components/common/SkeletonList.vue`（新建）

**Props**：

```ts
defineProps<{
  rows?: number   // 默认 6
  variant?: 'card' | 'row'  // 默认 'card'
}>()
```

**实现**：用 `el-skeleton` + `el-skeleton-item` 拼接 N 行占位卡，外观贴合 `event-card` / `incident-card` / `rule-card` 的几何（左色条 + 主体 grid）。`variant='row'` 用于 ChannelsView / ObjectsView 这种紧凑列表。

**Token 使用**：`background: var(--bg-elev-2)`，`linear-gradient(90deg, var(--bg-elev-2), var(--line-strong), var(--bg-elev-2))` 作为 shimmer 占位。`@keyframes skel` 已在 AiSummaryCard 里定义过，可在 `global.css` 提取共享或在新组件里复用。

**集成**：五个 ListView 在 `v-loading="loading"` 下方加：

```vue
<SkeletonList v-if="loading && !events.length" :rows="6" />
```

`v-loading` 仍保留用于增量刷新，但首屏 loading 由 SkeletonList 占位（与 spinner 互斥呈现）。Element 的 `v-loading` 默认是覆盖层，要把它改成静默 `:lock="false"` 或干脆首屏用 `v-if` 控制隐藏。

### B3. NetworkBanner 全局连接状态条（满足 Req 6）

**位置**：`frontend/src/components/layout/NetworkBanner.vue`（新建）

**挂载点**：`App.vue` 顶部，`<main class="app-main">` 之前：

```vue
<NetworkBanner :sse-connected="sseConnected" @reconnect="onReconnect" />
```

App.vue 已有 `sseConnected` ref，`onReconnect` 调用 `useSse` 暴露的 `reconnect()`（需补，给 useSse 新增 `reconnect` export）。

**HTTP 健康监控**：新建 composable

```ts
// frontend/src/composables/useHttpHealth.ts
export function useHttpHealth() {
  const recentFailures = ref<number[]>([])  // 5xx 时间戳数组
  const FAILURE_THRESHOLD = 3
  const WINDOW_MS = 5 * 60 * 1000

  // 在 http.ts 拦截器里调 reportFailure(status)
  function reportFailure() {
    const now = Date.now()
    recentFailures.value = [...recentFailures.value.filter(t => now - t < WINDOW_MS), now]
  }

  const unhealthy = computed(() => recentFailures.value.length >= FAILURE_THRESHOLD)
  return { reportFailure, unhealthy }
}
```

`http.ts` 的 axios `interceptors.response.use` error 分支补一行 `if (status >= 500) httpHealth.reportFailure()`。store 化后供 `NetworkBanner` 订阅。

**断开 30 秒计时**：`NetworkBanner` 内部 `watch(sseConnected, (v) => { if (!v) startTimer(); else stopTimer() })`，timer 30s 后置 `disconnected = true`。SSE 重连成功时 `disconnected = false` 并 setTimeout 3s 后从 DOM 移除（满足 Req 6.3）。

**DOM 结构**：

```vue
<transition name="banner-slide">
  <div v-if="visible" class="net-banner" :class="severity">
    <span class="dot" />
    <span class="msg">{{ message }}</span>
    <button class="reconnect" @click="$emit('reconnect')" :disabled="reconnecting">
      <RefreshCw :size="12" /> {{ reconnecting ? '重连中…' : '重新连接' }}
    </button>
  </div>
</transition>
```

**Token**：`background: var(--warn-soft)` + `color: var(--warn)` 作为默认（轻警告）；高频 5xx 升级为 `var(--danger-soft)` / `var(--danger)`。

**布局规则**：使用 `position: sticky; top: 0` 让它"挤出"主内容区域，而不是 absolute 覆盖（满足 Req 6.6）。App.vue 的 `.app-shell` grid 不动，banner 直接占据普通文档流中的一行。

### B4. ErrorPage 404/500 兜底（满足 Req 7）

**位置**：`frontend/src/views/ErrorPage.vue`（新建）

**Props**：

```ts
defineProps<{
  variant: '404' | '500'
  message?: string
  onRetry?: () => void  // variant='500' 时使用
}>()
```

**404 路由配置**：`router/index.ts` 末尾追加：

```ts
{ path: '/:pathMatch(.*)*', name: 'not-found',
  component: () => import('@/views/ErrorPage.vue'),
  props: { variant: '404' } }
```

**500 触发**：每个使用页面级首屏请求的 view（DashboardView / EventsView / IncidentsView / RulesView / ObjectsView / ChannelsView / AiStatsView）改造 `loadAll()`：

```ts
const error500 = ref<{ retry: () => void } | null>(null)
async function loadAll() {
  try {
    error500.value = null
    // ... 原首屏请求 ...
  } catch (e: any) {
    if (e?.response?.status >= 500) {
      error500.value = { retry: loadAll }
    }
  }
}
```

template 顶层包：

```vue
<ErrorPage v-if="error500" variant="500" :on-retry="error500.retry" />
<div v-else class="dashboard-v">…原内容…</div>
```

App chrome（侧栏 + 顶栏）保留可见，因为 ErrorPage 是 RouterView 内部内容（满足 Req 7.5）。

**视觉**：套用 `terminal-shell` 视觉模式 + 大数字 "404" / "500"（用 `--font-display` 96px）+ 一段说明 + 按钮（404 跳 dashboard、500 retry）。颜色全部 token：背景 `--bg-elev-1`，主数字 `--accent`（404）或 `--critical`（500）。

### C1. 后端：LlmModelConfig 单价（满足 Req 12, 14）

**Entity 改动**：`LlmModelConfig.java` 加两个字段：

```java
@TableField("prompt_price_per_1k")
private BigDecimal promptPricePer1k;

@TableField("completion_price_per_1k")
private BigDecimal completionPricePer1k;
```

**SQL 改动**：

`backend/src/main/resources/schema-h2.sql` 的 `create table if not exists llm_model_config` 块末尾追加两列：

```sql
prompt_price_per_1k     decimal(10,4),
completion_price_per_1k decimal(10,4),
```

`backend/src/main/resources/schema-h2-upgrade.sql` 追加：

```sql
-- prompt / completion 单价（单位元/1000 token）
alter table llm_model_config
    add column if not exists prompt_price_per_1k decimal(10,4);
alter table llm_model_config
    add column if not exists completion_price_per_1k decimal(10,4);
```

`docs/schema.sql`（MySQL DDL 文档）的 `create table llm_model_config` 块同步加两列，类型 `decimal(10,4) default null comment 'Prompt 单价 元/1k token'`。

`docs/schema-upgrade.sql`（MySQL 增量脚本）同样加 idempotent prepared 语句块，跟随该文件已建立的"`information_schema.columns` 探测 + dynamic SQL"模式。

**精度**：`DECIMAL(10,4)` 支持 `999999.9999`，覆盖任何已知模型的元/千 token 单价。

### C2. 后端：AiCallStatsService（满足 Req 8-13）

**位置**：`backend/src/main/java/com/aiops/alert/service/ai/AiCallStatsService.java`（新建）

**对外方法**：

```java
public class AiCallStatsService {
    AiStatsOverviewResponse loadOverview();           // Hero + 场景分布 + 7 日趋势 + 今日/本月成本
    List<AiCallLogResponse> slowTop(int days, int limit);  // 慢调用 Top N
    PageResult<AiCallLogResponse> page(AiCallLogQuery q);  // 流水分页
    AiCallLogResponse get(Long id);                   // 单条详情
}
```

**实现策略**：照搬 `DashboardService` 的 Java 端聚合套路：

```java
// 今日 / 昨日窗口
LocalDate today = LocalDate.now();
LocalDateTime todayStart = today.atStartOfDay();
LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();

List<AiCallLog> todayLogs = mapper.selectList(new LambdaQueryWrapper<AiCallLog>()
    .ge(AiCallLog::getCreatedAt, todayStart)
    .lt(AiCallLog::getCreatedAt, tomorrowStart));
// 同理 yesterdayLogs

long totalCalls = todayLogs.size();
long totalTokens = todayLogs.stream()
    .mapToInt(l -> nullSafe(l.getPromptTokens()) + nullSafe(l.getCompletionTokens()))
    .sum();
double successRate = todayLogs.isEmpty() ? 0.0
    : todayLogs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count() * 100.0
       / todayLogs.size();
```

成本估算用单独方法：

```java
BigDecimal calcCost(AiCallLog log, Map<Long, LlmModelConfig> priceIndex) {
    if (log.getModelConfigId() == null) return BigDecimal.ZERO;
    LlmModelConfig cfg = priceIndex.get(log.getModelConfigId());
    if (cfg == null) return BigDecimal.ZERO;
    BigDecimal pp = nullSafeBd(cfg.getPromptPricePer1k());
    BigDecimal cp = nullSafeBd(cfg.getCompletionPricePer1k());
    BigDecimal pt = BigDecimal.valueOf(nullSafe(log.getPromptTokens()));
    BigDecimal ct = BigDecimal.valueOf(nullSafe(log.getCompletionTokens()));
    return pt.multiply(pp).add(ct.multiply(cp))
        .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
}
```

**场景归一**：`scene` 不在 `{NL2RULE, EVENT_SUMMARY, CHAT, THRESHOLD}` 中的归到 `OTHER`：

```java
String normalizeScene(String s) {
    if (s == null) return "OTHER";
    return Set.of("NL2RULE","EVENT_SUMMARY","CHAT","THRESHOLD").contains(s) ? s : "OTHER";
}
```

**7 日趋势**：

```java
LocalDateTime weekAgo = today.minusDays(6).atStartOfDay();
List<AiCallLog> weekLogs = mapper.selectList(new LambdaQueryWrapper<AiCallLog>()
    .ge(AiCallLog::getCreatedAt, weekAgo));
Map<LocalDate, Long> byDate = weekLogs.stream()
    .collect(Collectors.groupingBy(
        l -> l.getCreatedAt().toLocalDate(),
        Collectors.counting()));
List<TrendItem> trend = new ArrayList<>();
for (int i = 0; i < 7; i++) {
    LocalDate d = today.minusDays(6 - i);
    trend.add(new TrendItem(d.toString(), byDate.getOrDefault(d, 0L)));
}
```

### C3. 后端：API 契约

**Controller**：`backend/src/main/java/com/aiops/alert/controller/AiCallStatsController.java`（新建）

```
GET  /api/ai-stats/overview                 → AiStatsOverviewResponse
GET  /api/ai-stats/slow?days=7&limit=10     → List<AiCallLogResponse>
GET  /api/ai-stats/logs?scene=&modelName=&status=&page=1&size=20 → PageResult<AiCallLogResponse>
GET  /api/ai-stats/logs/{id}                → AiCallLogResponse  // 含完整 payload
```

**DTO 定义**：

```java
@Data @Builder
class AiStatsOverviewResponse {
    // Hero
    long todayCallTotal;
    long todayTokenTotal;
    double todaySuccessRate;
    long yesterdayCallTotal;
    long yesterdayTokenTotal;
    double yesterdaySuccessRate;

    // 场景分布
    List<SceneStat> sceneDistribution;     // {scene, callCount, tokenTotal, tokenPercent}

    // 7 日趋势
    List<TrendItem> sevenDayTrend;         // {date, callCount}

    // 成本
    BigDecimal todayCost;
    BigDecimal monthCost;
    String costCurrency;                   // "CNY"
}

@Data @Builder
class AiCallLogResponse {
    Long id;
    String scene;
    Long modelConfigId;
    String modelName;
    Integer promptTokens;
    Integer completionTokens;
    Integer totalTokens;     // 派生
    Integer durationMs;
    String status;
    String errorMessage;
    String reasoningContent; // 详情接口才返回
    String requestPayload;   // 详情接口才返回
    String responsePayload;  // 详情接口才返回
    BigDecimal estimatedCost;// 派生
    LocalDateTime createdAt;
}

@Data
class AiCallLogQuery {
    String scene;
    String modelName;
    String status;
    @Min(1) Integer page = 1;
    @Min(1) @Max(100) Integer size = 20;
}

@Data @Builder
class PageResult<T> {
    long total;
    int page;
    int size;
    List<T> records;
}
```

`/logs` 列表接口默认不返回 `requestPayload` / `responsePayload` / `reasoningContent` 三个大字段（性能 + 隐私），通过 `/logs/{id}` 单条获取。

### C4. 前端：AiStatsView.vue（满足 Req 8-13）

**位置**：`frontend/src/views/AiStatsView.vue`（新建）

**整体结构**：

```vue
<template>
  <ErrorPage v-if="error500" variant="500" :on-retry="loadAll" />
  <div v-else class="ai-stats-v">
    <!-- Hero -->
    <section class="hero">…3 个指标卡 + 环比指示…</section>

    <!-- 双图表行 -->
    <section class="chart-row">
      <div class="panel-block">
        <div class="eyebrow">SCENE DISTRIBUTION</div>
        <div ref="sceneRef" class="chart" />
      </div>
      <div class="panel-block">
        <div class="eyebrow">7-DAY TIMELINE</div>
        <div ref="trendRef" class="chart" />
      </div>
    </section>

    <!-- 成本卡 + 慢调用 -->
    <section class="row-2">
      <div class="cost-card panel-block">…今日 / 本月¥…</div>
      <div class="slow-card panel-block">…慢调用 Top 10 表格 + 展开行…</div>
    </section>

    <!-- 调用流水 -->
    <section class="panel-block log-list">
      <div class="filter-row">…3 个 el-select + 分页…</div>
      <SkeletonList v-if="logsLoading" :rows="8" variant="row" />
      <table v-else>…</table>
    </section>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :size="640">…JSON viewer…</el-drawer>
  </div>
</template>
```

**echarts**：复用 `DashboardView` 的 `readToken()` 函数；环形图配色 `[--accent, --warn, --ok, --critical, --text-muted]` 五个场景；折线 `[--accent]` 单系列。`watch(theme.isDark, () => requestAnimationFrame(render))` 重渲染。

**Hero 环比指示**：

```ts
function compareDelta(today: number, yesterday: number): { sign: '+'|'-'|'='; pct: string } {
  if (yesterday === 0 && today === 0) return { sign: '=', pct: '持平' }
  if (yesterday === 0) return { sign: '+', pct: '新增' }
  const delta = ((today - yesterday) / yesterday) * 100
  if (Math.abs(delta) < 0.5) return { sign: '=', pct: '持平' }
  return { sign: delta > 0 ? '+' : '-', pct: `${Math.abs(delta).toFixed(0)}%` }
}
```

视觉：`+` 用 `--ok`，`-` 用 `--critical`，`=` 用 `--text-muted`。

**慢调用 Top 10 展开行**：行内 `<tr v-if="expanded === log.id">` 渲染 `<pre>{{ log.requestPayload }}</pre>` 与 `<pre>{{ log.responsePayload }}</pre>`，`background: var(--bg-elev-2)`，`font-family: var(--font-mono)`。

**流水列表筛选**：`filters` 三个 el-select（scene / modelName / status）+ `el-pagination`。Watch filters/pagination → `loadLogs()`。

### C5. 前端：API 文件（满足 Req 8-13）

**位置**：`frontend/src/api/aiStats.ts`（新建）

```ts
import { http } from './http'

export interface AiStatsOverview { /* 镜像后端 DTO */ }
export interface AiCallLogItem { /* 同上 */ }
export interface AiCallLogPage { total: number; page: number; size: number; records: AiCallLogItem[] }

export function getAiStatsOverview() {
  return http.get<AiStatsOverview>('/ai-stats/overview')
}
export function listSlowAiCalls(params: { days?: number; limit?: number } = {}) {
  return http.get<AiCallLogItem[]>('/ai-stats/slow', { params })
}
export function listAiCallLogs(params: AiCallLogQuery) {
  return http.get<AiCallLogPage>('/ai-stats/logs', { params })
}
export function getAiCallLog(id: number) {
  return http.get<AiCallLogItem>(`/ai-stats/logs/${id}`)
}
```

### C6. 前端：路由与侧栏（满足 Req 15）

**`router/index.ts`** 在 settings 之后追加：

```ts
{
  path: '/ai-stats',
  name: 'ai-stats',
  component: () => import('@/views/AiStatsView.vue'),
  meta: { title: 'AI 调用统计', icon: 'Activity' }
},
{
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/views/ErrorPage.vue'),
  meta: { title: '页面未找到' },
  props: { variant: '404' }
}
```

**侧栏自动生效**：`AppSidebar.vue` 的 `items` 已经从 `router.options.routes` 自动派生，只需在 `iconMap` 中加 `Activity`：

```ts
import { Activity } from 'lucide-vue-next'
const iconMap: Record<string, unknown> = {
  LayoutDashboard, BellRing, Flame, Sparkles, Server, Send, Settings, Activity
}
```

`title` 用 "AI 调用统计"。当前路由匹配 `/ai-stats` 时，`router-link` 的 `active-class="active"` 会自动应用选中态（Req 15.4 满足）。

`router.afterEach` 已设置 `document.title`（Req 15.3 满足）。



## Data Models

### AiCallLog（已存在，复用）

| 列 | Java 类型 | DB 类型 | 说明 |
|---|---|---|---|
| id | Long | bigint pk | |
| scene | String | varchar(32) | NL2RULE / EVENT_SUMMARY / THRESHOLD / CHAT |
| modelConfigId | Long | bigint | 关联 llm_model_config |
| modelName | String | varchar(128) | 调用时模型名快照 |
| requestPayload | String | clob | LLM 请求 JSON |
| responsePayload | String | clob | LLM 响应 JSON |
| promptTokens | Integer | int | |
| completionTokens | Integer | int | |
| durationMs | Integer | int | |
| status | String | varchar(16) | SUCCESS / FAILED |
| errorMessage | String | varchar(1024) | |
| reasoningContent | String | clob | 推理模型思考过程 |
| createdAt | LocalDateTime | timestamp | |

### LlmModelConfig（已存在，加 2 列）

| 新列 | Java 类型 | DB 类型 | 说明 |
|---|---|---|---|
| promptPricePer1k | BigDecimal | decimal(10,4) | 元/1k prompt token，可空 |
| completionPricePer1k | BigDecimal | decimal(10,4) | 元/1k completion token，可空 |

### AlertEvent / MetricSample（已存在，回填用）

无 schema 改动。回填时直接用 mapper.insert，时间字段显式赋值过去 7 天的 `LocalDateTime`。`event_no` 沿用 `ALERT-yyyyMMdd-{shortId}` 格式但 `yyyyMMdd` 用历史日期保持时间一致性。

## Error Handling

### 后端

| 场景 | 行为 |
|---|---|
| `/ai-stats/logs` 查询无结果 | 返回 `PageResult{total:0, records:[]}`，HTTP 200 |
| `/ai-stats/logs/{id}` 不存在 | 抛 `BizException("AI 调用流水不存在")` → `Result{code: !=0}` → 前端 ElMessage |
| 单条 AiCallLog 的 `modelConfigId` 不存在或为 NULL | `calcCost` 返回 `BigDecimal.ZERO`（满足 Req 12.4） |
| `prompt_price_per_1k` / `completion_price_per_1k` 为 NULL | `nullSafeBd` 返回 `BigDecimal.ZERO`（满足 Req 12.3） |
| 7 日趋势查询某天无数据 | byDate map 缺失，回退 0L（满足 Req 8 / 10 空状态行为） |

### 前端

| 场景 | 行为 |
|---|---|
| Hero 接口 5xx | `error500` ref 置位 → `ErrorPage variant='500'` 替换主区，App chrome 保留 |
| `/ai-stats/logs` 5xx（流水列表，非首屏关键） | 不切到 ErrorPage，列表区显示 `el-empty` + ElMessage 报错（避免误把次要表格失败升级为整页 500） |
| 流水列表过滤参数变更但请求未返回 | 表格主体显示 `SkeletonList`，分页器禁用 |
| 详情抽屉打开但 `requestPayload` 为空 | 占位文字 "无 prompt 记录"，避免空白抽屉 |
| 路由跳到 `/ai-stats` 但接口长时间无响应 | 沿用 axios 30s 超时；超时由 http.ts 统一 ElMessage 提示 |
| SSE 断开 ≥ 30s | NetworkBanner 黄色（warn-soft）"实时连接已断开" |
| 5xx 5min ≥ 3 次 | NetworkBanner 升级为红色（danger-soft）"接口异常 · 检查后端" |
| 上述两条同时发生 | 优先红色（danger 严重度更高） |

## Theme Compatibility

每个新建组件必须在 dark + light 主题下 visual diff。验证清单：

- 文本对比度：用浏览器 DevTools Lighthouse Accessibility 模式（或 axe）抽样验证主要文本块 ≥ 4.5:1（针对 normal-size text）。
- echarts 重渲染：`watch(theme.isDark, () => requestAnimationFrame(renderAll))` 确保切换主题瞬间所有图表跟随。
- 硬编码颜色检查：`grep '#[0-9a-fA-F]\{6\}' src/views/AiStatsView.vue src/components/{layout,common}/{NetworkBanner,SkeletonList,ErrorPage}.vue` 必须为零匹配（除非是注释里的 token 文档）。
- 透明度：仅使用 `--*-soft` 系列变量或 `color-mix(in srgb, var(--accent) 12%, transparent)`，禁用 `rgba(具体rgb值, .x)` 字面量。

## Performance

- AiCallStatsService 的所有聚合都在 Java 端完成，避开 H2/MySQL 方言差异。`/overview` 单次最多扫描 2 天 × 估算 1k 行 = 2k AiCallLog 记录；JVM 内存可控。
- 7 日趋势接口数据量 = 7 天 × ~500 条记录最大 = 3.5k 行，全表 selectList 后 Java groupingBy。压力可接受（dashboard 已用同样模式 7 天 alert_event）。
- 慢调用 Top 10 接口走 `orderByDesc(durationMs).last("limit 10")`，单次返回 10 行，但要把列表型字段（`requestPayload` `responsePayload` `reasoningContent`）映射到 DTO 时只在 `getById` 路径返回。
- DemoDataService.seed() 整体耗时 < 5s（事务内一次性 ~12 events + ~2k metric_samples），用户感知 OK。

## Validation Plan

按需求文档的 17 个 Requirement 逐条验证：

| Req | 验证方式 |
|---|---|
| 1 | seed 后 SQL 抽查：`select count(*), date(first_triggered_at) from alert_event group by 2`，应有 ≥7 个分组每组 ≥1 |
| 2 | 抽查 `select id, ai_summary_status, ai_summary from alert_event where ai_summary_status='SUCCESS'`，断言 `ai_summary` 是合法 JSON 且四字段齐全；至少 1 条 PENDING |
| 3 | `select count(*) from metric_sample group by object_id, metric_code`，每组 ≥100；分位计算 `P50<>P95` |
| 4 | 浏览器：触发一次 `summarizeAlertEvent` → 详情抽屉看 PENDING → SUCCESS 切换无布局抖动；devtools 暂停看 DOM 节点 ID 不变 |
| 5 | 5 个 ListView 首屏刷新（清缓存）：均看到 SkeletonList，无 spinner，无空白 |
| 6 | 模拟：①后端关停 → 30s 后 banner 出现 ②后端 500（强制 controller 抛异常）→ 5min 内点 3 次 → banner 升级红 ③ 后端恢复 → banner 3s 内消失 |
| 7 | ①浏览器 `/不存在路径` → 404 ②mock dashboard 5xx（postman block）→ 主区 500 但侧栏在 |
| 8-12 | 进入 `/ai-stats` 检查 hero 数字 / 环比 / 环形图 / 趋势 / 成本卡 / 慢调用 |
| 13 | 切换 scene/model/status filter，看分页和数据；点行打开抽屉看 4 字段 |
| 14 | 已有 H2 dev 库执行 `schema-h2-upgrade.sql` → 列存在、旧数据无丢失 |
| 15 | 侧栏出现"AI 调用统计"项；点击切到 `/ai-stats`；选中态正确；tab title 包含 "AI 调用统计 · AIOps Alert" |
| 16 | grep 硬编码颜色 = 零；切换 dark/light 截图对比 |
| 17 | `git diff` 检查不动 SettingsView/AiSummaryCard SUCCESS 块/CommandPalette；后端 `git diff` 检查无字段重命名 |

## Open Questions（保留 design 阶段决策）

下列在 requirements.md 标记的"待澄清事项"在此 design 文档中已敲定：

1. **5xx 计数窗口**：5 分钟滚动窗口，3 次阈值（已写入 `useHttpHealth` 常量） ✅
2. **MetricSample 历史下限**：每 (object, metric) ≥ 100 条 ✅
3. **预生成摘要数量**：3 条（覆盖最近一周不同对象类型） ✅
4. **ErrorPage 500 触发范围**：仅页面级首屏数据请求 5xx；流水列表等次要请求不升级 ✅
5. **AiStatsView 菜单位置**：放在 SettingsView 之后单独一项，图标 `Activity`（lucide），文案"AI 调用统计" ✅
6. **服务/控制器命名**：`AiCallStatsService` / `AiCallStatsController` ✅
7. **价格列精度**：`DECIMAL(10,4)` ✅

剩余可在实现期间灵活决定（不阻塞 tasks 编排）：

- AiCallLogQuery 是否要支持时间范围筛选？— 第一版只按 scene/model/status，时间范围 v2 再加。
- 详情抽屉是否要支持 JSON 折叠/语法高亮？— 第一版用 `<pre>` + `--font-mono`，不引入 JSON viewer 依赖。



## Correctness Properties

下列性质必须在实现完成后成立（任一不成立即视为本 feature 未交付）：

### Property 1: 历史时间分布性

**Validates: Requirements 1.1, 1.2**

`select count(distinct date(first_triggered_at)) from alert_event` 在 seed 后等于 7（覆盖近 7 个完整日历日，含今日则 ≥ 7）。

### Property 2: 预生成摘要 JSON 合法性

**Validates: Requirements 2.1, 2.2**

对所有 `ai_summary_status='SUCCESS'` 的 AlertEvent，`ai_summary` 字段经 `JSON.parse` 后含非空字符串字段 `what` `impact`、长度 ≥1 的字符串数组 `causes` `actions`。

### Property 3: PENDING 残留

**Validates: Requirements 2.4**

`select count(*) from alert_event where ai_summary_status='PENDING' or ai_summary_status is null` ≥ 1。

### Property 4: MetricSample 分位差

**Validates: Requirements 3.1, 3.2, 3.3**

对每个 (object_id, metric_code) 维度，`percentile(0.95) - percentile(0.50) > 0`（在 Java 端断言以避开方言差异）。

### Property 5: AiSummaryCard 节点稳定

**Validates: Requirements 4.3**

PENDING → SUCCESS 时 `.ai-summary-card` 根节点的 DOM 引用不变（前端 devtools `$0` 验证）。

### Property 6: NetworkBanner 时序

**Validates: Requirements 6.1, 6.3**

SSE 断开后 0~30s 内不显示，30s 后显示；恢复 0~3s 内消失。

### Property 7: ErrorPage Chrome 保留

**Validates: Requirements 7.5**

500 形态下 `document.querySelector('.sidebar')` 与应用 header 仍存在。

### Property 8: 成本计算单调性

**Validates: Requirements 12.2, 12.3, 12.4**

当 `prompt_tokens` 或 `completion_tokens` 增加时，`estimated_cost` 不降；当单价为 NULL/0 时，`estimated_cost = 0`。

### Property 9: 场景归一覆盖

**Validates: Requirements 9.1**

`AiStatsOverviewResponse.sceneDistribution` 中所有切片的 `callCount` 之和等于今日 AiCallLog 总数（即归一后无遗漏）。

### Property 10: Token-only 渲染

**Validates: Requirements 16.1, 16.2, 16.3**

在 dark / light 两个主题下分别截图 AiStatsView / NetworkBanner / SkeletonList / ErrorPage，颜色完全跟随 CSS 变量（无任何不变颜色）。

### Property 11: Schema 升级不破坏

**Validates: Requirements 14.3, 14.4, 17.4**

执行 `schema-h2-upgrade.sql` 前后 `select * from llm_model_config` 行数不变、原有列值不变。

### Property 12: API 契约负载剥离

**Validates: Requirements 11.3, 13.7**

`/ai-stats/logs` 列表响应不返回 `requestPayload` / `responsePayload` / `reasoningContent` 三个大字段；`/ai-stats/logs/{id}` 才返回。

## Testing Strategy

本仓库当前没有自动化测试基线（`backend/pom.xml` 仅含编译插件，前端无 vitest）。本 feature 不引入新的测试框架，但要求实现期通过下列手动验证步骤：

### 后端

1. **DemoSeed 端到端**：
   - `POST /api/demo/clean` → `POST /api/demo/seed`
   - `GET /api/dashboard` 检查 `sevenDayTrend` 数组 7 个元素 `total` 之和 ≥ 12
   - SQL：`select date(first_triggered_at), count(*) from alert_event group by 1` 确认 7 天每天 ≥1
   - SQL：`select count(*) from metric_sample where sampled_at >= now() - interval 7 day` 应 ≥ 100 × 已启用规则关联的 metric 个数
2. **AiCallStats 接口**：
   - 让系统跑一次 NL2Rule + 一次 EventSummary 触发 AiCallLog 写入
   - `GET /api/ai-stats/overview` 检查 hero 数字与 SQL `select count(*) from ai_call_log where date(created_at)=current_date` 一致
   - `GET /api/ai-stats/slow?days=7&limit=10` 返回数据按 `duration_ms` 降序
   - `GET /api/ai-stats/logs?scene=NL2RULE` 仅返回该场景记录
3. **价格列升级幂等**：
   - 已有 dev H2 库执行 `schema-h2-upgrade.sql` 一次 → 再执行一次 → 无报错、列只有一份
   - `select prompt_price_per_1k from llm_model_config` 全为 NULL（旧数据）

### 前端

1. **打字机 PENDING**：
   - 触发演示告警 → 立即点开详情 → AiSummaryCard 显示打字机循环消息
   - 等 LLM 返回 → 切换到 SUCCESS 四段卡片 → 用 devtools 看 `.ai-summary-card` 元素的 `__vnode` 引用是否一致（验证 5）
2. **SkeletonList 5 视图**：
   - 强制清空浏览器缓存进入每个 ListView，前 200ms 应显示 skeleton；切回路由再进入复用缓存数据时也应显示 skeleton（首屏每次显示）
3. **NetworkBanner 三场景**：
   - 后端关闭：30s 后 banner 黄；后端恢复：banner 3s 内消失
   - 后端 mock 5xx（用 OPS 工具临时改 controller 返回 `Result.fail`）：5min 内点 3 个不同接口 → banner 红
   - 点击重连按钮 → useSse.reconnect() 触发 → banner 显示"重连中"
4. **ErrorPage**：
   - 浏览器输入 `/foo-bar` → 404 页 + 跳 dashboard 按钮
   - mock `/api/dashboard` 返回 500 → DashboardView 主区 500 + 侧栏可见
5. **AiStatsView 全功能**：
   - Hero 三数字与 SQL 抽查一致；环形图色彩用 token；切到 light 主题图表色彩跟随
   - 慢调用展开：行内显示 prompt + response 完整 JSON，等宽字体
   - 成本卡：今日 ¥ 数字 = sum(estimated_cost where today)；本月数字同理
   - 流水筛选：选 scene=NL2RULE → 列表只剩对应行；分页器工作
   - 点行 → 抽屉显示 4 字段
   - 双主题截图对比，无错色
6. **路由 + 侧栏**：
   - 侧栏菜单出现"AI 调用统计"项 + Activity 图标
   - 点击切到 `/ai-stats`，document.title 包含 "AI 调用统计 · AIOps Alert"
   - 当前路由匹配时菜单项选中态正确

### 跨模块回归

1. seed → 启动故事模式 → 等 30~60s 自动告警 → AI 摘要流式生成（PENDING → SUCCESS）→ ⌘K 命令面板 → 切到 `/ai-stats` 看新写入的 AiCallLog 计入今日 hero 数字
2. 切到 light 主题再走一次上面流程，所有视觉一致

