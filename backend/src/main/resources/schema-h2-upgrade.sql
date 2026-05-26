-- =====================================================================
-- AIOps Alert · H2 增量升级脚本（idempotent）
-- 用 H2 的 `add column if not exists` / `add ... if not exists` 兜底新加字段，
-- 让已存在的旧库平滑升级到当前结构。
-- =====================================================================

-- ai_reasoning 列：alert_event 详情页展示思考过程
alter table alert_event
    add column if not exists ai_reasoning clob;

-- reasoning_content 列：ai_call_log 留痕思考过程
alter table ai_call_log
    add column if not exists reasoning_content clob;
