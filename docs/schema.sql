-- =====================================================================
-- AIOps Alert · Database Schema
-- 创建顺序：基础字典 → 业务表 → 关联表 → 流水表 → AI 相关
-- =====================================================================

create database if not exists `aiops_alert` default charset utf8mb4 collate utf8mb4_unicode_ci;
use `aiops_alert`;

-- ----------------------------------------------------------------
-- 1. 监控对象
-- ----------------------------------------------------------------
drop table if exists `monitor_object`;
create table `monitor_object` (
  `id` bigint not null auto_increment comment '主键',
  `object_code` varchar(64) not null comment '对象编码',
  `object_name` varchar(128) not null comment '对象名称',
  `object_type` varchar(32) not null comment '类型：SERVER/DATABASE/SYNC_JOB/PROCESS_JOB',
  `owner_name` varchar(64) null comment '负责人',
  `owner_phone` varchar(32) null comment '负责人电话',
  `tags` varchar(255) null comment '标签，逗号分隔',
  `status` varchar(16) not null default 'ENABLED' comment '状态：ENABLED/DISABLED',
  `description` varchar(500) null comment '描述',
  `ext_config` json null comment '扩展配置：IP/端口/作业编码 等',
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  unique key `uk_monitor_object_code` (`object_code`),
  key `idx_monitor_object_type` (`object_type`),
  key `idx_monitor_object_status` (`status`)
) engine=InnoDB comment='监控对象';

-- ----------------------------------------------------------------
-- 2. 告警渠道
-- ----------------------------------------------------------------
drop table if exists `alert_channel`;
create table `alert_channel` (
  `id` bigint not null auto_increment,
  `channel_code` varchar(64) not null comment '渠道编码',
  `channel_name` varchar(128) not null comment '渠道名称',
  `channel_type` varchar(32) not null comment '类型：WECOM/EMAIL/SMS',
  `provider_name` varchar(128) null comment '服务商',
  `status` varchar(16) not null default 'ENABLED',
  `priority` int not null default 100,
  `config_json` json null comment '渠道配置',
  `description` varchar(500) null,
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  unique key `uk_alert_channel_code` (`channel_code`),
  key `idx_alert_channel_type` (`channel_type`),
  key `idx_alert_channel_status` (`status`)
) engine=InnoDB comment='告警渠道';

-- ----------------------------------------------------------------
-- 3. 告警规则
-- ----------------------------------------------------------------
drop table if exists `alert_rule`;
create table `alert_rule` (
  `id` bigint not null auto_increment,
  `rule_code` varchar(64) not null,
  `rule_name` varchar(128) not null,
  `object_type` varchar(32) not null comment '规则适用的对象类型',
  `condition_logic` varchar(8) not null default 'AND' comment '多条件关系：AND/OR',
  `trigger_times` int not null default 1 comment '连续触发次数',
  `time_window_minutes` int not null default 5 comment '观察窗口(分钟)',
  `min_alert_interval_minutes` int not null default 30 comment '最小告警间隔(分钟)',
  `alert_level` varchar(16) not null comment '级别：NOTICE/NORMAL/SERIOUS/CRITICAL',
  `recover_notify` tinyint not null default 1,
  `repeat_notify` tinyint not null default 0,
  `status` varchar(16) not null default 'ENABLED',
  `priority` int not null default 100,
  `notify_title_template` varchar(255) null,
  `notify_content_template` text null,
  `description` varchar(500) null,
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  unique key `uk_alert_rule_code` (`rule_code`),
  key `idx_alert_rule_object_type` (`object_type`),
  key `idx_alert_rule_status` (`status`)
) engine=InnoDB comment='告警规则';

-- ----------------------------------------------------------------
-- 4. 告警规则触发条件（一条规则可有多个条件）
-- ----------------------------------------------------------------
drop table if exists `alert_rule_condition`;
create table `alert_rule_condition` (
  `id` bigint not null auto_increment,
  `rule_id` bigint not null,
  `condition_order` int not null default 1,
  `metric_code` varchar(64) not null comment '指标编码',
  `metric_name` varchar(128) not null,
  `compare_op` varchar(16) not null comment '比较符：GT/GE/LT/LE/EQ/NE/OFFLINE/FAILED/TIMEOUT/IN',
  `threshold_value` varchar(128) null,
  `threshold_unit` varchar(32) null,
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  key `idx_arc_rule` (`rule_id`),
  key `idx_arc_metric` (`metric_code`)
) engine=InnoDB comment='告警规则触发条件';

-- ----------------------------------------------------------------
-- 5. 规则↔对象 关联
-- ----------------------------------------------------------------
drop table if exists `alert_rule_object_rel`;
create table `alert_rule_object_rel` (
  `id` bigint not null auto_increment,
  `rule_id` bigint not null,
  `object_id` bigint not null,
  `created_at` datetime not null default current_timestamp,
  primary key (`id`),
  unique key `uk_aror` (`rule_id`,`object_id`),
  key `idx_aror_object` (`object_id`)
) engine=InnoDB comment='规则与对象关联';

-- ----------------------------------------------------------------
-- 6. 规则↔渠道 关联
-- ----------------------------------------------------------------
drop table if exists `alert_rule_channel_rel`;
create table `alert_rule_channel_rel` (
  `id` bigint not null auto_increment,
  `rule_id` bigint not null,
  `channel_id` bigint not null,
  `receiver_value` varchar(500) null comment '接收人，如手机号/邮箱列表',
  `template_code` varchar(64) null,
  `created_at` datetime not null default current_timestamp,
  primary key (`id`),
  unique key `uk_arcr` (`rule_id`,`channel_id`)
) engine=InnoDB comment='规则与渠道关联';

-- ----------------------------------------------------------------
-- 7. 告警事件
-- ----------------------------------------------------------------
drop table if exists `alert_event`;
create table `alert_event` (
  `id` bigint not null auto_increment,
  `event_no` varchar(64) not null comment '事件编号',
  `incident_id` bigint null comment '所属 Incident',
  `rule_id` bigint not null,
  `object_id` bigint not null,
  `object_type` varchar(32) not null,
  `object_name` varchar(128) not null,
  `metric_code` varchar(64) not null,
  `metric_name` varchar(128) not null,
  `alert_level` varchar(16) not null,
  `event_status` varchar(16) not null default 'PENDING' comment 'PENDING/CONFIRMED/RECOVERED/CLOSED',
  `current_value` varchar(255) null,
  `threshold_value` varchar(255) null,
  `event_title` varchar(255) not null,
  `event_content` text null,
  `event_reason` varchar(500) null,
  `ai_summary` text null comment 'AI 生成的告警摘要 (JSON)',
  `ai_summary_status` varchar(16) null comment 'AI 摘要状态：PENDING/SUCCESS/FAILED',
  `first_triggered_at` datetime not null default current_timestamp,
  `last_triggered_at` datetime not null default current_timestamp,
  `confirmed_at` datetime null,
  `recovered_at` datetime null,
  `closed_at` datetime null,
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  unique key `uk_alert_event_no` (`event_no`),
  key `idx_alert_event_rule` (`rule_id`),
  key `idx_alert_event_object` (`object_id`),
  key `idx_alert_event_status` (`event_status`),
  key `idx_alert_event_level` (`alert_level`),
  key `idx_alert_event_incident` (`incident_id`),
  key `idx_alert_event_first_at` (`first_triggered_at`)
) engine=InnoDB comment='告警事件';

-- ----------------------------------------------------------------
-- 8. 事件处理流水
-- ----------------------------------------------------------------
drop table if exists `alert_event_handle_log`;
create table `alert_event_handle_log` (
  `id` bigint not null auto_increment,
  `event_id` bigint not null,
  `action_type` varchar(32) not null comment 'CONFIRM/RECOVER/CLOSE/COMMENT',
  `before_status` varchar(16) null,
  `after_status` varchar(16) null,
  `operator_name` varchar(64) null,
  `operator_phone` varchar(32) null,
  `action_comment` varchar(1000) null,
  `created_at` datetime not null default current_timestamp,
  primary key (`id`),
  key `idx_aehl_event` (`event_id`)
) engine=InnoDB comment='告警事件处理流水';

-- ----------------------------------------------------------------
-- 9. 通知发送流水
-- ----------------------------------------------------------------
drop table if exists `alert_notify_log`;
create table `alert_notify_log` (
  `id` bigint not null auto_increment,
  `event_id` bigint not null,
  `rule_id` bigint not null,
  `channel_id` bigint not null,
  `channel_type` varchar(32) not null,
  `receiver_value` varchar(500) null,
  `notify_title` varchar(255) null,
  `notify_content` text null,
  `send_status` varchar(16) not null default 'PENDING' comment 'PENDING/SUCCESS/FAILED',
  `provider_msg_id` varchar(128) null,
  `failure_reason` varchar(1000) null,
  `sent_at` datetime null,
  `created_at` datetime not null default current_timestamp,
  primary key (`id`),
  key `idx_anl_event` (`event_id`),
  key `idx_anl_rule` (`rule_id`),
  key `idx_anl_channel` (`channel_id`),
  key `idx_anl_status` (`send_status`)
) engine=InnoDB comment='告警通知流水';

-- ----------------------------------------------------------------
-- 10. Incident（告警归并组）
-- ----------------------------------------------------------------
drop table if exists `alert_incident`;
create table `alert_incident` (
  `id` bigint not null auto_increment,
  `incident_no` varchar(64) not null,
  `object_id` bigint not null,
  `object_type` varchar(32) not null,
  `object_name` varchar(128) not null,
  `top_level` varchar(16) not null comment '当前最高级别',
  `event_count` int not null default 1,
  `status` varchar(16) not null default 'OPEN' comment 'OPEN/CLOSED',
  `summary` text null comment 'AI 简报',
  `first_event_at` datetime not null,
  `last_event_at` datetime not null,
  `closed_at` datetime null,
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  unique key `uk_incident_no` (`incident_no`),
  key `idx_incident_object` (`object_id`),
  key `idx_incident_status` (`status`)
) engine=InnoDB comment='告警归并组';

-- ----------------------------------------------------------------
-- 11. LLM 模型配置
-- ----------------------------------------------------------------
drop table if exists `llm_model_config`;
create table `llm_model_config` (
  `id` bigint not null auto_increment,
  `config_code` varchar(64) not null,
  `config_name` varchar(128) not null,
  `provider` varchar(32) not null comment 'OPENAI/QWEN/DEEPSEEK/CUSTOM',
  `base_url` varchar(255) not null,
  `api_key` varchar(255) not null,
  `model_name` varchar(128) not null,
  `temperature` decimal(3,2) not null default 0.20,
  `max_tokens` int not null default 2048,
  `is_default` tinyint not null default 0,
  `status` varchar(16) not null default 'ENABLED',
  `description` varchar(500) null,
  `created_at` datetime not null default current_timestamp,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  primary key (`id`),
  unique key `uk_llm_code` (`config_code`)
) engine=InnoDB comment='LLM 模型配置';

-- ----------------------------------------------------------------
-- 12. LLM 调用留痕
-- ----------------------------------------------------------------
drop table if exists `ai_call_log`;
create table `ai_call_log` (
  `id` bigint not null auto_increment,
  `scene` varchar(32) not null comment 'NL2RULE/EVENT_SUMMARY/THRESHOLD/CHAT',
  `model_config_id` bigint null,
  `model_name` varchar(128) null,
  `request_payload` mediumtext null,
  `response_payload` mediumtext null,
  `prompt_tokens` int null,
  `completion_tokens` int null,
  `duration_ms` int null,
  `status` varchar(16) not null default 'SUCCESS' comment 'SUCCESS/FAILED',
  `error_message` varchar(1000) null,
  `created_at` datetime not null default current_timestamp,
  primary key (`id`),
  key `idx_aicl_scene` (`scene`),
  key `idx_aicl_status` (`status`)
) engine=InnoDB comment='AI 调用留痕';

-- ----------------------------------------------------------------
-- 13. 指标历史样本（用于阈值推荐 + 模拟器写入）
-- ----------------------------------------------------------------
drop table if exists `metric_sample`;
create table `metric_sample` (
  `id` bigint not null auto_increment,
  `object_id` bigint not null,
  `metric_code` varchar(64) not null,
  `metric_value` varchar(64) not null comment '数值或枚举',
  `numeric_value` decimal(20,4) null comment '便于聚合',
  `sampled_at` datetime not null default current_timestamp,
  primary key (`id`),
  key `idx_metric_object_code_time` (`object_id`,`metric_code`,`sampled_at`)
) engine=InnoDB comment='指标历史样本';
