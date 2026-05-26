# AIOps Alert · E2E UI Tests

Playwright 端到端 UI 自动化测试，覆盖 8 个 view + 主题切换 + AI 摘要 PENDING 打字机 + NOC 大屏模式 + 命令面板等关键交互。

## 快速开始

```bash
# 装依赖（首次）
npm install
npx playwright install chromium

# 后端 + 前端必须先跑起来
# - 后端: backend/ 下 mvnw spring-boot:run -> http://localhost:8090
# - 前端: frontend/ 下 npm run dev -> http://localhost:5173

# 跑全套（dark + light 双主题，~5min）
npx playwright test

# 单主题
npx playwright test --project=chromium-dark
npx playwright test --project=chromium-light

# 单 spec
npx playwright test 01-dashboard
```

## 测试覆盖矩阵

| Spec | 覆盖内容 |
|---|---|
| `01-dashboard.spec.ts` | Hero + KPI + 7 日趋势 + DailyBriefCard + NOC 模式 |
| `02-events.spec.ts` | 列表三栏 + 实时 SSE 推送 + AiSummaryCard PENDING 打字机过渡 |
| `03-rules-ai.spec.ts` | AI 终端横幅 + NlRuleDialog 终端外壳 |
| `04-ai-stats.spec.ts` | AI 调用统计 Hero/双图表/慢调用展开/流水筛选/抽屉 |
| `05-navigation.spec.ts` | 8 项侧栏中文 + 全 view 路由 + 404 + Cmd+K + 主题切换 |

## 报告

跑完后看 HTML 报告：

```bash
npx playwright show-report
```

失败时自动留 trace + 视频，路径 `test-results/`。
