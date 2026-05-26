-- =====================================================================
-- AIOps Alert · MySQL 增量升级脚本
-- 在已部署的旧库上执行，兜底添加新字段。MySQL 不支持 add column if not exists，
-- 所以用 information_schema 探测后再 alter。
-- =====================================================================

-- 1. alert_event.ai_reasoning
set @col_exists := (
  select count(*) from information_schema.columns
   where table_schema = database()
     and table_name = 'alert_event'
     and column_name = 'ai_reasoning'
);
set @sql := if(@col_exists = 0,
  'alter table alert_event add column ai_reasoning text null comment ''AI 思考过程'' after ai_summary',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2. ai_call_log.reasoning_content
set @col_exists := (
  select count(*) from information_schema.columns
   where table_schema = database()
     and table_name = 'ai_call_log'
     and column_name = 'reasoning_content'
);
set @sql := if(@col_exists = 0,
  'alter table ai_call_log add column reasoning_content mediumtext null comment ''AI 思考过程'' after error_message',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
