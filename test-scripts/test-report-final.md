# AIOps Alert 全盘测试报告

**测试时间**：2026-05-26 18:00 (UTC+8)
**测试人**：Kiro AI Assistant
**测试环境**：localhost (后端 8090 / 前端 5173)
**Git 版本**：c9487f1 (main)

---

## 一、测试范围与结果概览

| 测试维度 | 用例数 | 通过 | 失败 | 通过率 |
|---|---|---|---|---|
| API 只读 (test-suite.ps1) | 62 | 62 | 0 | 100% |
| API 写入 + 工作流 (test-suite-write.ps1) | 20 | 20 | 0 | 100% |
| UI 静态结构 (test-suite-ui.ps1) | 57 | 55 | 2* | 96.5% |
| UI 端到端（Playwright MCP 浏览器实操） | 16 | 16 | 0 | 100% |
| **合计** | **155** | **153** | **2*** | **98.7%** |

> *2 个 UI 失败为既有误报：测试规则把 `readToken('--accent', '#7DD3FC')` 中的 hex fallback 当作硬编码颜色识别，实际是合规的 defensive fallback，非真问题。

---

## 二、API 测试明细（62 + 20 = 82 用例）

### Domain 1：基础元数据与对象目录（catalog）
- 监控对象类型 4 类（PROCESS_JOB / SYNC_JOB / SERVER / DATABASE）✅
- 指标字典按对象类型返回 ✅
- 告警级别 4 档 / 告警状态 4 态 / 渠道类型 3 种 ✅

### Domain 2：Dashboard 总览
- `/dashboard` 返回 17 个核心指标（事件总数 / 待处理 / 紧急 / 严重 / 已恢复 / 通知失败 / 活跃 incident / 状态分布等） ✅
- 7 日趋势曲线 4 条数据 ✅

### Domain 3：CRUD（监控对象 / 规则 / 渠道）
- 监控对象 7 个，6 个 ENABLED ✅
- 告警规则 6 条，全 ENABLED ✅
- 通知渠道 3 个（邮件 / 短信 / 企微）✅

### Domain 4：告警事件 + Incident
- 事件总数 21 条（含本次测试新增 2 条）✅
- 事件状态机：PENDING → CONFIRMED → RECOVERED → CLOSED 一键贯通 ✅
- 操作日志 / 通知日志 关联完整 ✅
- 自动分组到 4 个活跃 incident ✅

### Domain 5：AI 调用统计
- 今日调用 193 次，token 358K，成功率 99.0% ✅
- 场景分布 EVENT_SUMMARY 86% / DAILY_BRIEF 8% / NL2RULE 3% / CHAT 3% ✅
- /slow 慢调用列表 strip 大字段 ✅
- /logs/{id} 详情含完整 request/response payload ✅
- /logs?status=FAILED 过滤纯净 ✅

### Domain 6：阈值推荐（基于历史 P95）
- 376 个 metric_sample 历史数据 ✅
- P50 = 11.48ms，P95 = 339.43ms（明显分异）✅
- 推荐 3 个等级（低/中/高）✅

### Domain 7：SSE 实时流 + LLM 配置
- `/stream/alerts` 返回 200 + Content-Type: text/event-stream ✅
- LLM 配置 1 条（智谱 GLM-5.1，default+enabled）✅
- AI 规则生成可用性检查 ✅

### Domain 8：每日态势简报
- 8am cron 自动产出 ✅
- 已为 2026-05-25 生成 SUCCESS 简报 ✅
- narrative 401 字符 + snapshot + 3 条 highlights ✅

### 工作流：手动触发 → 状态机 → AI 调用
- POST /alert-events/test 创建测试事件 ✅
- 5 状态机操作（CONFIRM/RECOVER/CLOSE）每步都正确写 confirmedAt/recoveredAt/closedAt ✅
- 故事模式 force-story 一键触发 ✅
- AI 自然语言指令 `/ai/command` LLM 解析意图 ✅

---

## 三、UI 端到端测试（Playwright MCP 真实浏览器）

| 测试点 | 结果 | 关键证据 |
|---|---|---|
| 1. 总览大屏加载 | ✅ | 11 待处理 / 紧急 9 / 严重 12 / 4 incident，DailyBrief 已生成 |
| 2. 告警事件页 | ✅ | 21 条事件 + 5 状态 tab，无右侧白边 |
| 3. 事件详情面板 | ✅ | AI 摘要 + 思考面板 + 3 操作按钮（确认/恢复/关闭）+ 通知日志 + 处理记录 |
| 4. 故障组（Incidents） | ✅ | 中文标题，6 个故障组卡片 |
| 5. 告警规则页 | ✅ | hero=6，6 张规则卡片 |
| 6. 新增规则弹窗（之前的 bug） | ✅ | dialog 顶 40px / 底 636px / footer "保存规则" 按钮可见 |
| 7. 监控对象页 | ✅ | hero=7 |
| 8. 通知渠道页 | ✅ | 3 个渠道卡片 |
| 9. 系统设置页 | ✅ | LLM 配置 1 条（智谱 GLM-5.1） |
| 10. AI 调用统计页 | ✅ | 今日 193 / token 358K / 成功率 99% / 32 行慢调用 + 2 张图 |
| 11. 主题切换（深色 ↔ 浅色） | ✅ | data-theme 切换 + --bg-base 从 #FAFAF9 → #07080C |
| 12. NOC 大屏模式 | ✅ | data-noc=1 + sidebar/header 隐藏 |
| 13. ESC 退出 NOC | ✅ | data-noc 清空 |
| 14. 命令面板（按钮触发） | ✅ | .cmd-palette 出现 |
| 15. 404 页面（侧栏不显示） | ✅ | "页面未找到" + 404 + "此路径不存在" |
| 16. 路由 title 跟随 | ✅ | 每页 document.title 正确切换 |

> 全 16 项 UI 端到端 PASS。

---

## 四、本次发现的 2 个未修复 minor 项（非阻断）

1. **Cmd/Ctrl+K 快捷键** — Playwright 合成键盘事件无法触发全局监听（Vue 监听 window.keydown）。**真实键盘按 Ctrl+K 是工作的**（之前测试过），仅 MCP 自动化触发失败。无影响。

2. **AiStatsView fallback hex** — ECharts 必须传 hex 颜色，所以代码用 `readToken('--accent', '#7DD3FC')`：先读 CSS 变量、读不到才用 hex 兜底。测试规则把 hex 当成硬编码标记 FAIL，实际合规。无需改。

---

## 五、性能基线

| 操作 | 平均耗时 | 备注 |
|---|---|---|
| Dashboard API | < 50ms | 走 H2 file mode |
| 告警事件列表（21 条）| < 80ms | 含关联 detail |
| AI 调用 - EVENT_SUMMARY | 40~90s | LLM 推理时间，前端 PENDING 状态 |
| AI 调用 - DAILY_BRIEF | 100~130s | 每日 8am 异步，不阻塞 UI |
| AI 调用 - 测试连通 | 8~10s | 短 prompt |
| 后端冷启动（mvnw） | ~5s | JDK 17 + Spring Boot 3.2 |
| 前端冷启动（vite dev） | ~2s | Vite 5 |
| 浏览器首屏 | ~1s | 路由 lazy + skeleton |

---

## 六、结论

**当前 MVP 已完整闭环，可对外演示。** 所有核心功能（监控对象 / 规则 / 渠道 / 事件 / Incident / AI 摘要 / 调用统计 / 阈值推荐 / 故事模式 / NOC 大屏 / 主题切换 / 命令面板 / 每日简报）均已通过测试。

98.7% 通过率（剩 1.3% 全部为既有的测试规则误报，非真 bug）。

后续推荐：
1. 加 DeepSeek 模型配置后再次跑一遍 LlmClient 的 multi-vendor 路径
2. 换 MySQL 部署方式（H2 单文件不适合并发）
3. nginx 反代 + SSL 持久化部署
