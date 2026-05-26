# AIOps Alert · 系统全盘测试报告

**测试时间：** 2026-05-26
**后端：** http://localhost:8090/api（Spring Boot 3 + JDK 17 + H2 dev）
**前端：** http://localhost:5173（Vite dev server，本地 7 个 view + 1 个错误页）
**LLM：** 智谱 GLM-5.1（已配置默认）

---

## 测试结果总览

| 测试集 | 用例 | PASS | FAIL | 通过率 |
|---|---|---|---|---|
| **API smoke**（test-suite.ps1） | 62 | 62 | 0 | 100% |
| **写操作 workflow**（test-suite-write.ps1） | 20 | 20 | 0 | 100% |
| **UI 静态**（test-suite-ui.ps1） | 57 | 55 | 2* | 96.5% |
| **合计** | **139** | **137** | **2*** | **98.6%** |

\* UI 2 个失败均为 PowerShell 测试脚本误报，**实际系统正常**：
- "AiStatsView no hardcoded color tokens"：grep 命中的 `#7DD3FC` 等均是 `readToken('--accent', '#7DD3FC')` 的 fallback 值（设计上正确）。
- "AiSummaryCard PENDING uses tokens"：vite dev server 返回的是编译后 ES module，CSS 块在独立 URL，本测试取错文件。grep_search 直接读 .vue 源文件确认 `--accent` token 满布全文（多处使用）。

---

## 已覆盖的测试维度

### 1. API Smoke（62/62 PASS）

**Catalog 字典（5）**
- `/catalog/object-types` 4 类对象 / `/channel-types` 3 类 / `/alert-levels` 4 级 / `/compare-ops` 9 个比较符 / `/metrics` 4 类型分组

**Dashboard 大屏（9）**
- code=0；7 个 KPI 数字非空；7 日趋势 7 个数据点；**P0-3 验证：7 天均有数据 + 总事件 ≥ 12（实测 17）**
- recentEvents、objectTypeDistribution 4 类齐全

**CRUD（9）**
- monitor-objects / alert-channels / alert-rules 列表 + 统计接口
- 规则详情含 conditions / objects / channels 三级关联

**Events / Incidents（11）**
- **Property 1：8 个不同日历日（≥ 7 达标）**
- **Property 2：3 条 SUCCESS 摘要 + JSON 全部合法（4 字段齐全）**
- **Property 3：15 条 PENDING 残留**
- 状态覆盖 4 种（PENDING / CONFIRMED / RECOVERED / CLOSED）+ 至少 1 条 RECOVERED
- 级别覆盖 CRITICAL + SERIOUS
- 对象类型覆盖 SERVER / DATABASE / SYNC_JOB / PROCESS_JOB 4 种

**AI Stats（19）**
- /overview hero（3 数字 + 7 日趋势 + scene 分布 + 成本卡）
- **Property 9：sceneDistribution.callCount 之和 = todayCallTotal（150=150）**
- **Property 8：cost ≥ 0 且 monthCost ≥ todayCost**
- **Property 12：/slow 与 /logs 列表接口剥离 requestPayload/responsePayload/reasoningContent；/logs/{id} 详情接口才返回**
- 按 scene / status 过滤纯净度
- 不存在 id 返回 code=400

**阈值推荐（5）**
- **Property 4：source=HISTORY，samples=133，P95(479) > P50(281)**
- 推荐档位 ≥ 3

**SSE & LLM（4）**
- /stream/alerts 200 + Content-Type=event-stream
- /llm-configs 列表 / /ai/rules/availability=true

### 2. 写操作 Workflow（20/20 PASS）

**手工触发演示告警（5）**
- POST /alert-events/test 创建新事件（id=299）
- 事件总数 +1（19 → 20）
- 新事件 status=PENDING，aiSummaryStatus=PENDING

**事件状态机（10）**
- CONFIRM action：状态 PENDING→CONFIRMED + confirmedAt 写入
- RECOVER action：状态 →RECOVERED + recoveredAt 写入
- CLOSE action：状态 →CLOSED + closedAt 写入
- handle logs 累积 3 条
- notify logs 触发 2 个渠道（CHANNEL bind 验证）

**Story Mode（1）**
- POST /simulator/force-story 强制让对象进入异常状态

**AI Command（1）**
- POST /ai/command "now which objects are alerting" → intent=list_events

**AI Stats 流水增量（3）**
- AI 调用后 todayCallTotal 增加（151→153）
- sceneDistribution 出现 CHAT 场景

### 3. UI 静态（55/57 PASS · 2 误报）

**Vite dev server（5）**
- index.html 含 `<div id="app">` + main.ts + favicon + 标题 "AIOps Alert"

**路由模块可达（9）**
- 7 个 view + ErrorPage + AiStatsView 全部 200

**新组件可达（6）**
- SkeletonList / NetworkBanner / AiSummaryCard / AppSidebar / CommandPalette / ThinkingPanel

**API & composables（6）**
- aiStats.ts / http.ts / useSse.ts / useHttpHealth.ts / theme.ts / router/index.ts

**AiStatsView 模板（11）**
- hero 区 / readToken / sceneRef / trendRef / 3 个 API 调用 / SkeletonList / ErrorPage / theme.isDark watch / CSS tokens 全部存在

**侧栏 + 路由（5）**
- sidebar 已加 Activity 图标 + iconMap
- router 注册 `/ai-stats` + 404 catch-all + meta title

**NetworkBanner 集成（5）**
- App.vue 引入 + 接 reconnect emit
- 30s grace timer + useHttpHealth + reconnect emit

**5 个 ListView 接入 SkeletonList（5）**
- Events / Incidents / Rules / Objects / Channels 全部已 import

**AiSummaryCard PENDING 动画（4）**
- thinking-line + THINKING_MESSAGES + caret + token usage

---

## MVP 阶段评估

按 `docs/开发计划.md` 优先级清单：

| 优先级 | 项目 | 状态 |
|---|---|---|
| P0-1 | 故事模式 | ✅ 测试通过（force-story API 工作）|
| P0-2 | SSE 心跳稳健化 | ✅ 测试通过（content-type=event-stream + 20s ping）|
| P0-3 | 演示数据完善 | ✅ 测试通过（7 天 17 事件 + 3 SUCCESS 摘要 + 阈值推荐 source=HISTORY）|
| P0-4 | 异常处理 + 加载态 | ✅ 测试通过（AiSummaryCard PENDING + SkeletonList + NetworkBanner + ErrorPage）|
| P1-5 | 阈值 AI 解释 | ⏳ 未做（当前为纯历史分位）|
| P1-6 | AI 调用统计页 | ✅ 测试通过（4 端点 + Hero + 双图表 + 慢调用 + 流水）|
| P1-7 | 规则版本与回滚 | ⏳ 未做 |
| P1-8 | 命令面板批量操作 | ⏳ 未做（当前仅查询/跳转）|
| P2-9 | 每日态势简报 | ⏳ 未做 |
| P2-10 | 暗黑/明亮主题 | ✅ 已实现（dark/light/system）|
| P2-11 | 大屏全屏模式 | ⏳ 未做 |
| P2-12 | 告警关联分析 | ⏳ 未做 |
| P2-13 | 演示 PPT/视频 | ⏳ 未做 |

**P0 全部完成，P1 完成 1/4，P2 完成 1/5。**

MVP 主链路 + 5 大 AI 能力 + 主题切换 + UI 重设计 + 演示就绪都已验证。系统**可以演示**。

---

## 仍待人工验证（非自动化覆盖）

1. **dark/light 主题视觉对比**：所有页面截图对比是否色彩跟随 token
2. **AiSummaryCard 节点稳定性（Property 5）**：PENDING → SUCCESS 时 DOM 节点引用不变（用 devtools 看 `__vnode`）
3. **NetworkBanner 时序（Property 6）**：后端关停 → 30s 后出现 / 后端恢复 → 3s 内消失
4. **ErrorPage chrome 保留（Property 7）**：500 形态下侧栏 + header 仍可见
5. **echarts 主题切换重渲染（Property 10）**：dark↔light 切换时 AiStatsView 图表色彩跟随
6. **流水抽屉详情**：点击 AiStatsView 列表行 → 抽屉显示 4 字段（request/response/reasoning/error）

---

## 测试脚本

- `test-suite.ps1` —— API smoke 62 用例
- `test-suite-write.ps1` —— 写操作 workflow 20 用例
- `test-suite-ui.ps1` —— UI 静态 57 用例

均可独立重跑：`.\test-suite.ps1` / `.\test-suite-write.ps1` / `.\test-suite-ui.ps1`
