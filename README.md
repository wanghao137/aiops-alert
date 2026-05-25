# AIOps Alert · 智能监控告警系统

> 面向 AI 编程大赛的参赛项目。一句话定位：**AI-Native 的监控告警平台**，把传统告警系统里最繁琐的"建规则、看告警、查根因、归并降噪"四件事，用 AI 重做一遍。

## ✨ 核心特色

| 能力 | 说明 |
|---|---|
| 🧠 自然语言建规则 | 一句话生成完整告警规则，不用填表单 |
| 📝 AI 告警摘要 | 每条告警自动生成"发生了什么 / 影响范围 / 可能原因 / 建议动作" |
| 🔥 智能告警归并 | 同对象短时间多个告警自动合并为 Incident，避免刷屏 |
| 📊 阈值智能推荐 | 基于历史分位数推荐"高/中/低"敏感度阈值 |
| ⌘ 命令面板 | `Cmd+K` 唤起，自然语言查询当前告警态势 |

## 🏗️ 技术栈

**后端**
- Spring Boot 3.2 + Java 17
- MyBatis-Plus 3.5
- MySQL 8 + Redis 7
- SSE 实时推送
- OpenAI 兼容协议（支持自部署模型）

**前端**
- Vue 3 + TypeScript + Vite
- Element Plus + Tailwind CSS
- ECharts + lucide-vue-next
- Pinia + Vue Router

## 📁 目录结构

```
aiops-alert/
├── backend/              # Spring Boot 后端
├── frontend/             # Vue 3 前端
├── docs/                 # 需求 / 设计 / SQL
└── README.md
```

## 🚀 快速开始

### 准备环境
- JDK 17+
- Maven 3.8+
- Node 18+
- MySQL 8 / Redis 7

### 初始化数据库
```bash
mysql -uroot -p < docs/schema.sql
```

### 启动后端
```bash
cd backend
mvn spring-boot:run
# 默认 http://localhost:8090
```

### 启动前端
```bash
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

## 📐 模块拆解

| 模块 | 状态 |
|---|---|
| 监控对象管理 | 🟡 规划中 |
| 告警规则管理（含 NL 建规则） | 🟡 规划中 |
| 告警渠道管理 | 🟡 规划中 |
| 告警事件中心（含 AI 摘要） | 🟡 规划中 |
| 智能告警归并 / Incident | 🟡 规划中 |
| 总览大屏 | 🟡 规划中 |
| 命令面板 | 🟡 规划中 |

## 📖 文档

- [需求说明](docs/需求说明.md)
- [系统设计](docs/系统设计.md)
- [数据库设计](docs/schema.sql)
- [开发路线图](docs/路线图.md)

## 📜 License

仅供 AI 编程大赛使用。
