# Requirements Document

> 子标题：演示就绪与 AI 可观测 (demo-readiness-and-ai-observability)

## Introduction

本 feature 把演示前必须完成的三件事打成一个独立 spec，目标统一为「演示稳定 + AI 可观测」：

- **子模块 A · 演示数据完善（对应 P0-3）**：让 `DemoDataService.seed()` 把至少 12 条 AlertEvent 与 7 天 MetricSample 历史样本铺开到近 7 个日历日，并预生成 2 条以上 SUCCESS 状态的 AI 摘要，使总览大屏 7 日趋势图四条曲线（总量 / 待处理 / 已恢复 / 紧急）有真实曲线，且阈值推荐有可用分位数据。
- **子模块 B · 加载态与异常处理打磨（对应 P0-4）**：补全 AiSummaryCard 的 PENDING 视觉、统一 5 个 ListView 的首屏骨架屏、新增全局 NetworkBanner 与 404/500 ErrorPage，使运维工程师与演示主讲人在网络抖动、冷启动、错误路由情况下都看到一致的可控体验。
- **子模块 C · AI 调用统计页 / "AI 工程"（对应 P1-6）**：基于现有 `ai_call_log` 表新增 AiStatsView 页面、AiCallStatsService 后端、`/ai-stats` 路由与侧栏入口，给 Team Lead 和运维工程师提供 AI 用量、场景分布、慢调用、估算成本的可观测视图，并通过 `llm_model_config` 增加单价列支持成本估算。

本 feature 严格沿用已建立的 Editorial × Terminal 设计语言，所有颜色仅通过 DesignTokens 引用、所有 echarts 走 TokenAwareEcharts 模式，dark / light 双主题必须色彩正确。本 feature 不重写已 commit 的 SettingsView、AiSummaryCard SUCCESS / FAILED 状态、CommandPalette；schema 变更走 idempotent 增量脚本，不破坏现有 H2 dev 库数据。

## Glossary

- **DemoDataService**: 后端 Java 类 `com.aiops.alert.service.support.DemoDataService`。
- **DemoSeed**: `DemoDataService.seed()` 方法的一次完整执行。
- **AlertEvent**: `alert_event` 表的一条记录。
- **MetricSample**: `metric_sample` 表的一条记录。
- **AiCallLog**: `ai_call_log` 表的一条记录。
- **LlmClient**: 后端 Java 类 `com.aiops.alert.service.ai.LlmClient`，所有 LLM 调用入口。
- **AiSummaryCard**: 前端组件 `frontend/src/components/alert/AiSummaryCard.vue`。
- **ListView**: 包含五个列表型视图组件：EventsView、IncidentsView、RulesView、ObjectsView、ChannelsView。
- **SkeletonScreen**: 一种占位 UI 形态，以与最终内容布局一致的灰块阵列代替待加载数据。
- **NetworkBanner**: 全局顶部条带组件，用于提示连接异常。
- **ErrorPage**: 全屏错误页面组件，用于路由 404 与接口 500 兜底。
- **AiStatsView**: 前端新增页面 `frontend/src/views/AiStatsView.vue`。
- **AiCallStatsService**: 后端新增服务类 `com.aiops.alert.service.ai.AiCallStatsService`（命名暂定，design 阶段确认）。
- **AiCallStatsController**: 后端新增 REST 控制器 `com.aiops.alert.controller.AiCallStatsController`（命名暂定，design 阶段确认）。
- **LlmModelConfig**: `llm_model_config` 表的一条记录，以及对应实体类。
- **DesignTokens**: 在 `frontend/src/styles/global.css` 中定义的 CSS 自定义属性，例如 `--bg-elev-1`、`--accent`、`--line`、`--ok`、`--warn`、`--danger`、`--critical` 等。
- **TokenAwareEcharts**: echarts 配置模式，通过 `getComputedStyle(document.documentElement).getPropertyValue(name)` 读取 DesignTokens 后赋值给 echarts option，并在 ThemeStore.isDark 变化时重渲染。
- **DemoStoryteller**: 演示主讲人角色，在评委或客户面前演示系统，关注首屏视觉冲击、流畅度、不出现冷启动空白。
- **OperationsEngineer**: 运维工程师角色，每天使用系统响应告警，关注一致的加载态、网络异常提示与错误兜底。
- **TeamLead**: 团队 Leader 角色，关注全局告警态势、AI 用量与成本。
- **SystemAdmin**: 系统管理员角色，关注 schema 升级安全、路由与菜单结构。
- **ThemeStore**: 前端 Pinia store `frontend/src/stores/theme.ts`，暴露 `isDark` 响应式状态。

---

## Requirements

## 子模块 A · 演示数据完善

### Requirement 1: 历史 AlertEvent 分散到近 7 天

**User Story:** 作为 DemoStoryteller，我希望 DemoSeed 完成后总览大屏的 7 日趋势图四条曲线有真实数据点，以便首屏给观众的视觉是连续 7 天的运维仪表盘而非一条直线。

#### Acceptance Criteria

1. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 创建至少 12 条 AlertEvent 历史记录，且每条 AlertEvent 的 `first_triggered_at`、`last_triggered_at`、`created_at` 三个字段值均落在 DemoSeed 触发时刻之前的 7 个完整日历日窗口内。
2. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 使前 7 个完整日历日中的每一日至少包含 1 条且至多 3 条 AlertEvent 历史记录。
3. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 使生成的 AlertEvent 历史记录在 `alert_level` 维度上至少覆盖 NORMAL、SERIOUS、CRITICAL 三种级别中的至少 2 种。
4. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 使生成的 AlertEvent 历史记录在 `object_type` 维度上至少覆盖 SERVER、DATABASE、SYNC_JOB、PROCESS_JOB 四种对象类型中的至少 3 种。
5. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 使生成的 AlertEvent 历史记录在 `event_status` 维度上至少覆盖 PENDING、CONFIRMED、RECOVERED、CLOSED 四种状态中的至少 3 种，且至少包含 1 条 RECOVERED 记录。

### Requirement 2: 历史 AlertEvent 预生成 AI 摘要

**User Story:** 作为 DemoStoryteller，我希望 seed 后部分历史事件已经持有 SUCCESS 状态的 AI 摘要内容，以便演示时点开它们能直接看到完整四段式摘要而不必现场冷启动调用 LLM。

#### Acceptance Criteria

1. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 至少为 2 条 AlertEvent 历史记录把 `ai_summary_status` 字段设置为 SUCCESS。
2. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 保证所有 `ai_summary_status` 等于 SUCCESS 的 AlertEvent 历史记录的 `ai_summary` 字段为合法 JSON 字符串，且该 JSON 同时包含非空字符串字段 `what`、非空字符串字段 `impact`、长度大于等于 1 的字符串数组 `causes`、长度大于等于 1 的字符串数组 `actions`。
3. WHILE DemoSeed 写入预生成 AI 摘要内容, THE DemoDataService SHALL 通过预置字符串直接写入数据库，并 SHALL 不通过 LlmClient 触发任何 LLM 调用。
4. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 至少保留 1 条 `ai_summary_status` 为 PENDING 或 NULL 的 AlertEvent 记录，以便 DemoStoryteller 演示 AI 摘要现场生成路径。

### Requirement 3: 历史 MetricSample 分散到近 7 天

**User Story:** 作为 OperationsEngineer，我希望 seed 后阈值智能推荐能基于真实分位数据给出建议，以便演示「AI 阈值推荐」时返回的高 / 中 / 低三档数值看起来像生产数据而非经验值兜底。

#### Acceptance Criteria

1. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 为每条已启用 AlertRule 关联的每个数值型指标 `metric_code`，对每个该规则关联的 MonitorObject 写入至少 100 条 MetricSample 历史记录。
2. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 使每个 (object_id, metric_code) 维度的 MetricSample 历史记录的 `sampled_at` 值均落在 DemoSeed 触发时刻之前的 7 个完整日历日窗口内，并使前 7 个日历日中的每一日至少包含 1 条该 (object_id, metric_code) 维度的 MetricSample 记录。
3. WHEN DemoSeed 执行完成, THE DemoDataService SHALL 保证每个 (object_id, metric_code) 维度的 MetricSample 历史记录的 `numeric_value` 数值集合的 50 分位数与 95 分位数的差大于 0，即该样本集存在分位差异，可被阈值推荐流程消费。

---

## 子模块 B · 加载态与异常处理

### Requirement 4: AI 摘要 PENDING 状态视觉

**User Story:** 作为 DemoStoryteller，我希望事件详情页的 AiSummaryCard 在 `ai_summary_status` 为 PENDING 时显示明确的"AI 思考中"动画占位，以便演示「现场触发告警 → AI 摘要流式生成」路径时观众能感知到等待意图而不是看到空白。

#### Acceptance Criteria

1. WHILE 当前 AlertEvent 的 `ai_summary_status` 等于 PENDING, THE AiSummaryCard SHALL 在卡片可视区域内显示一个含有持续动画的"AI 思考中"占位视觉，该占位视觉至少包含一个动画指示元素（光标 / 打字机 / 旋转图标 / 呼吸点中的任一种）和一段说明文案。
2. WHILE 当前 AlertEvent 的 `ai_summary_status` 等于 PENDING, THE AiSummaryCard SHALL 不渲染 SUCCESS 状态下的四段式摘要内容（what / impact / causes / actions），亦 SHALL 不渲染 FAILED 状态下的错误提示。
3. WHEN 当前 AlertEvent 的 `ai_summary_status` 由 PENDING 变更为 SUCCESS, THE AiSummaryCard SHALL 用 SUCCESS 状态的四段式摘要替换 PENDING 占位视觉，并 SHALL 不引发布局抖动（即卡片外层 DOM 节点保持不变，仅替换内层内容区域）。
4. WHILE 当前 AlertEvent 的 `ai_summary_status` 等于 PENDING, THE AiSummaryCard SHALL 通过 DesignTokens 引用所有颜色，且 SHALL 不在样式中包含任何形如 `#xxxxxx` 或 `rgb(...)` 的硬编码颜色字面量。

### Requirement 5: 列表页统一骨架屏

**User Story:** 作为 OperationsEngineer，我希望 5 个 ListView 在首次进入时显示一致风格的骨架屏，以便我对加载状态形成稳定预期，不再被有的页面 spinner、有的页面空白、有的页面闪烁所打扰。

#### Acceptance Criteria

1. WHEN 一个 ListView 首次挂载且其首屏数据请求尚未返回, THE ListView SHALL 渲染一个 SkeletonScreen，且该 SkeletonScreen 的几何结构与最终列表的行布局保持视觉一致（行高、列宽、行间距相近）。
2. THE EventsView、IncidentsView、RulesView、ObjectsView、ChannelsView 共五个 ListView SHALL 复用同一个 SkeletonScreen 组件实现，使五个页面骨架屏的动画节奏与色块层级完全一致。
3. WHEN 一个 ListView 的首屏数据请求返回成功且列表至少包含 1 行, THE ListView SHALL 用真实数据行替换 SkeletonScreen 占位行，并 SHALL 在替换瞬间不引入任何额外的全屏 spinner 或全屏遮罩。
4. WHEN 一个 ListView 的首屏数据请求返回成功且列表为空, THE ListView SHALL 用既有的空状态视觉替换 SkeletonScreen，而非保持骨架屏继续显示。
5. THE SkeletonScreen 实现 SHALL 通过 DesignTokens（例如 `--bg-elev-2`、`--bg-elev-3`、`--line-strong`）引用所有颜色，且 SHALL 在 dark 与 light 主题下均显示视觉一致的占位灰阶。

### Requirement 6: 全局网络异常 NetworkBanner

**User Story:** 作为 OperationsEngineer，我希望 SSE 长连接断开或后端持续 5xx 时顶部出现一条明显的"实时连接已断开 · 重新连接"提示，以便我能立即知道告警可能漏推、并主动点击恢复连接。

#### Acceptance Criteria

1. WHILE 前端 SSE 长连接处于断开状态且断开持续时间大于等于 30 秒, THE NetworkBanner SHALL 显示在应用顶部，文案至少包含"实时连接已断开"与"重新连接"两段可见文字，并 SHALL 提供一个用户可点击的"重新连接"控件。
2. IF 前端在最近 5 分钟滚动窗口内累计接收到 3 次或以上 HTTP 5xx 响应, THEN THE NetworkBanner SHALL 显示在应用顶部，文案至少包含"实时连接已断开"与"重新连接"两段可见文字，并 SHALL 提供一个用户可点击的"重新连接"控件。
3. WHEN 前端 SSE 长连接由断开恢复为已连接, THE NetworkBanner SHALL 在恢复后 3 秒内自动隐藏，且不再占用顶部布局空间。
4. WHEN 用户点击 NetworkBanner 的"重新连接"控件, THE NetworkBanner SHALL 触发一次 SSE 重连尝试，并 SHALL 显示"重连中"过渡状态直至重连成功或失败。
5. THE NetworkBanner SHALL 通过 DesignTokens 引用所有颜色（背景使用 `--warn-soft`/`--danger-soft` 类语义色，文字使用 `--warn`/`--danger` 类语义色），且 SHALL 在 dark 与 light 主题下均色彩正确。
6. THE NetworkBanner SHALL 不遮挡当前 ListView 或 DashboardView 的可滚动区域，即出现 NetworkBanner 时其余内容区域 SHALL 整体下移，而非覆盖在内容之上。

### Requirement 7: 路由 404 与接口 500 ErrorPage

**User Story:** 作为 OperationsEngineer，我希望访问到不存在的路由或后端返回 5xx 时看到一个统一的、走设计 token 的 Editorial × Terminal 风格错误页，以便错误体验不破坏整体设计语言、且能引导我回到首页。

#### Acceptance Criteria

1. WHEN Vue Router 解析的路径与所有已定义路由均不匹配, THE ErrorPage SHALL 渲染 404 形态，包含可见的"404"标识、说明文案、以及一个跳转回 `/dashboard` 的可点击控件。
2. IF 一个 ListView 或 DashboardView 或 AiStatsView 的首屏页面级数据请求返回 HTTP 5xx, THEN THE ErrorPage SHALL 在该视图主内容区渲染 500 形态，包含可见的"500"标识、说明文案、以及一个用户可点击的"重试"控件。
3. WHEN 用户点击 ErrorPage 500 形态的"重试"控件, THE ErrorPage SHALL 触发一次原始首屏页面级数据请求的重新发起。
4. THE ErrorPage 的 404 形态与 500 形态 SHALL 通过 DesignTokens 引用所有颜色，且 SHALL 在 dark 与 light 主题下均色彩正确。
5. THE ErrorPage SHALL 不替换浏览器页面 title 之外的应用 chrome（应用顶栏 / 侧栏在 500 形态下保持可见，使用户可继续切换到其他视图）。

---

## 子模块 C · AI 调用统计页 / "AI 工程"

### Requirement 8: AiStatsView 顶部 Hero 区

**User Story:** 作为 TeamLead，我希望进入 AI 调用统计页时第一眼看到三个核心数字：今日 AI 总调用次数、今日 token 总量、今日成功率，以及它们与昨日的对比，以便我用 5 秒就能判断 AI 调用是否健康、是否在增长。

#### Acceptance Criteria

1. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 在顶部 Hero 区渲染三个并列指标卡：今日 AiCallLog 总数、今日 AiCallLog 的 `total_tokens` 之和（`total_tokens` 等于 `prompt_tokens` 加 `completion_tokens`）、今日 AiCallLog 中 `status` 等于 SUCCESS 的记录占比。
2. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 为 Hero 区每个指标卡同时显示一个相对昨日的环比变化文本（例如"+12%"或"-8%"或"持平"）以及一个方向指示视觉（向上 / 向下 / 持平三种状态之一）。
3. THE AiStatsView Hero 区指标卡定义"今日"为应用所在时区的当前自然日 0 时 0 分至当前时刻，"昨日"为该自然日的前一日 0 时 0 分至 23 时 59 分 59 秒。
4. THE AiStatsView Hero 区 SHALL 沿用 DashboardView 的 hero 视觉模式（包含 eyebrow 小标 / 大数字 / 副标说明文案三层结构），并 SHALL 通过 DesignTokens 引用所有颜色。
5. IF 今日 AiCallLog 数为 0, THEN THE AiStatsView Hero 区 SHALL 在每个指标卡显示数值"0"以及一段空状态说明文案，并 SHALL 不渲染任何环比方向指示视觉。

### Requirement 9: AiStatsView 场景分布

**User Story:** 作为 TeamLead，我希望看到今日 AI 调用按场景类别（NL2RULE / EVENT_SUMMARY / CHAT / THRESHOLD / OTHER）的分布，以便我了解哪些 AI 能力在被频繁使用、哪些被冷落。

#### Acceptance Criteria

1. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 渲染一个环形图，其切片对应今日 AiCallLog 按 `scene` 字段聚合后的调用次数分布，且 `scene` 字段值不属于 NL2RULE、EVENT_SUMMARY、CHAT、THRESHOLD 四种取值之一的记录 SHALL 被聚合到名为 OTHER 的切片中。
2. WHEN 环形图渲染完成, THE AiStatsView SHALL 在环形图旁边或图例中为每一个非空切片同时展示场景名称、调用次数、以及该场景的 `total_tokens` 之和占今日全部 AiCallLog 的 `total_tokens` 之和的百分比。
3. THE AiStatsView 场景分布环形图 SHALL 采用 TokenAwareEcharts 模式，颜色取自 DesignTokens（例如 `--accent`、`--warn`、`--ok`、`--critical`、`--text-muted`），且 SHALL 在 ThemeStore 的 `isDark` 状态变化后重渲染以匹配新主题。
4. IF 今日 AiCallLog 数为 0, THEN THE AiStatsView SHALL 用一段空状态说明文案替换环形图，且 SHALL 不渲染空环。

### Requirement 10: AiStatsView 7 日调用趋势

**User Story:** 作为 TeamLead，我希望看到过去 7 天 AI 调用次数的折线趋势，以便我观察 AI 用量是上升、下降还是平稳。

#### Acceptance Criteria

1. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 渲染一条折线图，其 X 轴覆盖前 7 个完整日历日加上今日合计 7 个数据点，每一个数据点的 Y 值为对应日历日内 AiCallLog 的总数。
2. THE AiStatsView 7 日趋势折线图 SHALL 采用 TokenAwareEcharts 模式，所有 echarts 颜色（系列色、轴线色、网格线色、tooltip 颜色）通过 DesignTokens 读取。
3. WHEN ThemeStore 的 `isDark` 状态发生变化, THE AiStatsView 7 日趋势折线图 SHALL 重新读取 DesignTokens 并重渲染，使新主题下 echarts 颜色与 CSS 主题保持一致。
4. WHEN 用户在折线图任一数据点上悬停, THE AiStatsView SHALL 在 tooltip 中同时展示该数据点对应的日历日（YYYY-MM-DD 格式）以及该日 AiCallLog 总数的整数值。

### Requirement 11: AiStatsView 慢调用 Top 10

**User Story:** 作为 OperationsEngineer，我希望看到最近 7 天耗时最高的 10 条 AI 调用记录，以便我能定位是哪个场景或模型导致 LLM 调用变慢，并下钻看 prompt 与 response 排查。

#### Acceptance Criteria

1. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 渲染一个慢调用列表，列表内容为 `created_at` 落在前 7 个完整日历日加今日窗口内、按 `duration_ms` 降序排序的前 10 条 AiCallLog 记录。
2. THE AiStatsView 慢调用列表的每一行 SHALL 同时展示 `duration_ms`（单位毫秒）、`scene`、`model_name`、`created_at`（精确到秒）、以及一个用户可点击的"展开"控件。
3. WHEN 用户点击慢调用列表某一行的"展开"控件, THE AiStatsView SHALL 在该行下方或右侧展示该 AiCallLog 的 `request_payload` 与 `response_payload` 完整内容。
4. THE AiStatsView 慢调用列表 SHALL 通过 DesignTokens 引用所有颜色，且耗时数值 SHALL 使用等宽字体（`--font-mono`）渲染。
5. IF 前 7 天加今日窗口内 AiCallLog 数为 0, THEN THE AiStatsView SHALL 用一段空状态说明文案替换慢调用列表。

### Requirement 12: AiStatsView 估算成本卡 与 LlmModelConfig 单价配置

**User Story:** 作为 TeamLead，我希望看到今日与本月的 AI 估算成本（人民币），以便我在汇报中能给出"AI 用了多少钱"的具体数字。

#### Acceptance Criteria

1. THE LlmModelConfig 表 SHALL 新增两个可空数值列 `prompt_price_per_1k` 与 `completion_price_per_1k`，分别表示该模型每 1000 个 prompt token 与每 1000 个 completion token 的单价（单位人民币元）。
2. WHEN AiCallStatsService 计算单条 AiCallLog 的估算成本, THE AiCallStatsService SHALL 使用以下公式：`(prompt_tokens / 1000) * prompt_price_per_1k + (completion_tokens / 1000) * completion_price_per_1k`，其中 `prompt_price_per_1k` 与 `completion_price_per_1k` 取自该 AiCallLog 的 `model_config_id` 关联的 LlmModelConfig 记录。
3. WHERE 一条 AiCallLog 的 `model_config_id` 关联 LlmModelConfig 的 `prompt_price_per_1k` 或 `completion_price_per_1k` 列值为 NULL, THE AiCallStatsService SHALL 把该 NULL 值视为数值 0 参与估算成本计算。
4. WHERE 一条 AiCallLog 的 `model_config_id` 列值为 NULL 或对应 LlmModelConfig 已不存在, THE AiCallStatsService SHALL 把该 AiCallLog 的估算成本视为数值 0。
5. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 渲染一张估算成本卡，同时展示今日估算成本之和与本月估算成本之和，单位均为人民币（前置符号"¥"），保留 2 位小数。
6. THE AiStatsView 估算成本卡所定义的"今日"为应用所在时区的当前自然日 0 时 0 分至当前时刻，"本月"为应用所在时区的当前自然月 1 日 0 时 0 分至当前时刻。

### Requirement 13: AiStatsView 调用流水列表

**User Story:** 作为 OperationsEngineer，我希望有一个分页的 AI 调用流水列表，可按场景 / 模型 / 状态过滤、按时间倒序，以便排查具体某次 AI 调用为什么失败或返回异常。

#### Acceptance Criteria

1. WHEN 用户首次进入 AiStatsView, THE AiStatsView SHALL 渲染一个分页的 AiCallLog 流水列表，默认按 `created_at` 降序排序，每页默认展示不超过 20 条记录。
2. THE AiStatsView 流水列表的每一行 SHALL 同时展示 `created_at`（精确到秒）、`scene`、`model_name`、`status`、`duration_ms`、`prompt_tokens`、`completion_tokens` 七个字段。
3. WHERE 用户在 AiStatsView 流水列表上方设置了 `scene` 筛选项, THE AiStatsView SHALL 仅展示 `scene` 字段值与该筛选项匹配的 AiCallLog 记录。
4. WHERE 用户在 AiStatsView 流水列表上方设置了 `model_name` 筛选项, THE AiStatsView SHALL 仅展示 `model_name` 字段值与该筛选项匹配的 AiCallLog 记录。
5. WHERE 用户在 AiStatsView 流水列表上方设置了 `status` 筛选项, THE AiStatsView SHALL 仅展示 `status` 字段值与该筛选项匹配的 AiCallLog 记录。
6. WHEN 用户切换分页页码或修改任一筛选项, THE AiStatsView SHALL 重新发起对应数据请求，并 SHALL 在请求未返回期间在表格主体区域显示 SkeletonScreen 占位（与 Requirement 5 复用同一组件）。
7. WHEN 用户点击流水列表某一行, THE AiStatsView SHALL 在抽屉或弹层中展示该 AiCallLog 的 `request_payload`、`response_payload`、`reasoning_content`、`error_message` 四个字段的完整内容。

### Requirement 14: 后端 schema 升级

**User Story:** 作为 SystemAdmin，我希望本 feature 的 schema 变更通过 idempotent 增量脚本生效，以便已运行的 H2 dev 库能平滑升级而不丢失既有数据。

#### Acceptance Criteria

1. THE `backend/src/main/resources/schema-h2-upgrade.sql` 文件 SHALL 新增 idempotent 形式的 `ALTER TABLE llm_model_config ADD COLUMN IF NOT EXISTS prompt_price_per_1k` 与 `ALTER TABLE llm_model_config ADD COLUMN IF NOT EXISTS completion_price_per_1k` 语句，列类型为可表示金额的小数类型（与 design 阶段确定的精度一致）。
2. THE `docs/schema.sql` 文件中 `llm_model_config` 表的 CREATE TABLE 语句 SHALL 同步包含 `prompt_price_per_1k` 与 `completion_price_per_1k` 两个可空列。
3. WHEN H2 dev 数据库已存在且包含本 feature 之前已写入的 LlmModelConfig 记录, THE schema-h2-upgrade.sql 的执行 SHALL 不删除任何已有数据行，亦 SHALL 不删除任何已有列；执行后已有 LlmModelConfig 记录的两个新列值为 NULL。
4. THE 本 feature 引入的 schema 变更 SHALL 不修改 `llm_model_config` 表已有任何列的列名、列类型或非空约束。

### Requirement 15: AiStatsView 路由项与侧栏入口

**User Story:** 作为 SystemAdmin，我希望 AiStatsView 在 Vue Router 与主侧栏菜单中注册一项可见入口，以便所有用户能从主侧栏点击进入 AI 调用统计页。

#### Acceptance Criteria

1. THE Vue Router 配置 SHALL 新增一项 `path` 等于 `/ai-stats`、`component` 指向 AiStatsView 的路由记录。
2. THE 应用主侧栏菜单 SHALL 新增一项菜单条目，其 `to` 指向 `/ai-stats`，并 SHALL 同时显示一个图标与一段中文文案（文案与图标在 design 阶段确认）。
3. WHEN 用户点击主侧栏的 AiStatsView 菜单条目, THE Vue Router SHALL 把当前路由切换到 `/ai-stats`，并 SHALL 把浏览器 tab title 设置为以 AiStatsView 中文菜单文案开头、以 ` · AIOps Alert` 结尾的字符串。
4. WHEN 当前路由为 `/ai-stats`, THE 主侧栏 SHALL 把 AiStatsView 菜单条目以"选中"视觉态呈现（与其余菜单条目"选中"视觉态保持一致）。

---

## 横切关注

### Requirement 16: 设计 token 与主题兼容

**User Story:** 作为 DemoStoryteller，我希望本 feature 新增的所有视觉在 dark 与 light 两种主题下都色彩正确、与既有 Editorial × Terminal 设计语言完全一致，以便在任何演示环境（含投屏环境）中都能呈现统一观感。

#### Acceptance Criteria

1. THE 本 feature 新增或修改的全部 Vue 组件 SHALL 通过 DesignTokens（例如 `--bg-elev-1`、`--accent`、`--line`、`--text-primary` 等 CSS 自定义属性）引用所有颜色，且 SHALL 不在样式中包含任何形如 `#xxxxxx`、`rgb(...)`、`rgba(...)` 的硬编码颜色字面量；引入透明度时 SHALL 使用 DesignTokens 中已定义的 `*-soft` 变量或基于变量的 `color-mix` 表达式。
2. THE 本 feature 新增的全部 echarts 实例 SHALL 采用 TokenAwareEcharts 模式（参考 DashboardView 的 `readToken` 函数），即所有图表颜色通过 `getComputedStyle(document.documentElement).getPropertyValue(name)` 从 DesignTokens 读取。
3. WHEN ThemeStore 的 `isDark` 状态发生变化, THE 本 feature 新增的全部 echarts 实例 SHALL 重新读取 DesignTokens 并重渲染，使新主题下图表颜色与 CSS 主题保持一致。
4. THE 本 feature 新增或修改的全部 Vue 组件 SHALL 在 dark 与 light 两种主题下均通过文本对比度 4.5:1 的可读性基线（针对正文文本，按 WCAG 2.1 AA 标准）。

### Requirement 17: 不破坏现有功能

**User Story:** 作为 SystemAdmin，我希望本 feature 不影响已 commit 在 main 分支的视图与流程，以便已通过测试的代码路径继续保持稳定。

#### Acceptance Criteria

1. THE 本 feature 的实现 SHALL 保留 SettingsView、AiSummaryCard SUCCESS 状态视觉、AiSummaryCard FAILED 状态视觉、CommandPalette 四者的现有 DOM 结构与样式不变（允许新增包装元素或新增 prop，但既有元素的标签、class 名、可见样式必须保持一致）。
2. THE 本 feature 的实现 SHALL 不删除任何当前已存在的后端 service 方法、controller endpoint、entity 字段，亦 SHALL 不修改任何当前已存在的 entity 字段的列名或非空约束。
3. THE 本 feature 的实现 SHALL 不删除任何当前已存在的前端 view、composable、store、API 函数，亦 SHALL 不修改任何当前已存在的 API 函数签名（参数列表 / 返回类型）。
4. THE 本 feature 引入的 schema 变更 SHALL 通过 `backend/src/main/resources/schema-h2-upgrade.sql` 增量脚本生效，且执行该脚本对一个已有 H2 dev 库的效果 SHALL 仅为新增列，而非新建表或删除既有数据。

---

## 待澄清事项（design 阶段确认）

下列条目在初版 requirement 中已给出可执行的具体取值，但用户原话未明确指定，design 阶段建议二次确认：

1. **5xx 累计计数窗口**（Req 6.2）：原文未指定时间窗，本文档定为"5 分钟滚动窗口内累计 ≥ 3 次"。
2. **MetricSample 历史下限**（Req 3.1）：本文档定为"每 (object_id, metric_code) ≥ 100 条样本"以保证分位数计算稳定性。
3. **预生成摘要数量**（Req 2.1）：用户原话"2-3 条"，本文档按"≥ 2 条"建模，后续可定为 3 条。
4. **ErrorPage 500 触发范围**（Req 7.2）：本文档限定为"页面级首屏数据请求 5xx"，避免任何字段级 5xx 都全屏覆盖。
5. **AiStatsView 菜单位置与图标**（Req 15.2）：用户原话"放在 SettingsView 边上 or 单独一项"，本文档要求"主侧栏存在该入口"，具体位置与图标由 design 阶段确定。
6. **AiCallStatsService / AiCallStatsController 命名**（Glossary）：暂定命名，design 阶段确认。
7. **价格列精度**（Req 14.1）：列类型留给 design 阶段（建议 `DECIMAL(10,4)` 以支持四位小数单价）。
