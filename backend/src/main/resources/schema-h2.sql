-- ===================================================================
-- AIOps Alert · H2 dev schema
-- 与 docs/schema.sql (MySQL) 字段保持一致，类型适配 H2
-- ===================================================================

-- 1. monitor_object
create table if not exists monitor_object (
  id            bigint auto_increment primary key,
  object_code   varchar(64) not null,
  object_name   varchar(128) not null,
  object_type   varchar(32) not null,
  owner_name    varchar(64),
  owner_phone   varchar(32),
  tags          varchar(255),
  status        varchar(16) default 'ENABLED' not null,
  description   varchar(500),
  ext_config    clob,
  created_at    timestamp default current_timestamp not null,
  updated_at    timestamp default current_timestamp not null,
  constraint uk_monitor_object_code unique (object_code)
);
create index if not exists idx_monitor_object_type on monitor_object (object_type);
create index if not exists idx_monitor_object_status on monitor_object (status);

-- 2. alert_channel
create table if not exists alert_channel (
  id              bigint auto_increment primary key,
  channel_code    varchar(64) not null,
  channel_name    varchar(128) not null,
  channel_type    varchar(32) not null,
  provider_name   varchar(128),
  status          varchar(16) default 'ENABLED' not null,
  priority        int default 100 not null,
  config_json     clob,
  description     varchar(500),
  created_at      timestamp default current_timestamp not null,
  updated_at      timestamp default current_timestamp not null,
  constraint uk_alert_channel_code unique (channel_code)
);
create index if not exists idx_alert_channel_type on alert_channel (channel_type);
create index if not exists idx_alert_channel_status on alert_channel (status);

-- 3. alert_rule
create table if not exists alert_rule (
  id                          bigint auto_increment primary key,
  rule_code                   varchar(64) not null,
  rule_name                   varchar(128) not null,
  object_type                 varchar(32) not null,
  condition_logic             varchar(8) default 'AND' not null,
  trigger_times               int default 1 not null,
  time_window_minutes         int default 5 not null,
  min_alert_interval_minutes  int default 30 not null,
  alert_level                 varchar(16) not null,
  recover_notify              tinyint default 1 not null,
  repeat_notify               tinyint default 0 not null,
  status                      varchar(16) default 'ENABLED' not null,
  priority                    int default 100 not null,
  notify_title_template       varchar(255),
  notify_content_template     clob,
  description                 varchar(500),
  created_at                  timestamp default current_timestamp not null,
  updated_at                  timestamp default current_timestamp not null,
  constraint uk_alert_rule_code unique (rule_code)
);
create index if not exists idx_alert_rule_object_type on alert_rule (object_type);
create index if not exists idx_alert_rule_status on alert_rule (status);

-- 4. alert_rule_condition
create table if not exists alert_rule_condition (
  id              bigint auto_increment primary key,
  rule_id         bigint not null,
  condition_order int default 1 not null,
  metric_code     varchar(64) not null,
  metric_name     varchar(128) not null,
  compare_op      varchar(16) not null,
  threshold_value varchar(128),
  threshold_unit  varchar(32),
  created_at      timestamp default current_timestamp not null,
  updated_at      timestamp default current_timestamp not null
);
create index if not exists idx_arc_rule on alert_rule_condition (rule_id);
create index if not exists idx_arc_metric on alert_rule_condition (metric_code);

-- 5. alert_rule_object_rel
create table if not exists alert_rule_object_rel (
  id         bigint auto_increment primary key,
  rule_id    bigint not null,
  object_id  bigint not null,
  created_at timestamp default current_timestamp not null,
  constraint uk_aror unique (rule_id, object_id)
);
create index if not exists idx_aror_object on alert_rule_object_rel (object_id);

-- 6. alert_rule_channel_rel
create table if not exists alert_rule_channel_rel (
  id              bigint auto_increment primary key,
  rule_id         bigint not null,
  channel_id      bigint not null,
  receiver_value  varchar(500),
  template_code   varchar(64),
  created_at      timestamp default current_timestamp not null,
  constraint uk_arcr unique (rule_id, channel_id)
);

-- 7. alert_event
create table if not exists alert_event (
  id                   bigint auto_increment primary key,
  event_no             varchar(64) not null,
  incident_id          bigint,
  rule_id              bigint not null,
  object_id            bigint not null,
  object_type          varchar(32) not null,
  object_name          varchar(128) not null,
  metric_code          varchar(64) not null,
  metric_name          varchar(128) not null,
  alert_level          varchar(16) not null,
  event_status         varchar(16) default 'PENDING' not null,
  current_value        varchar(255),
  threshold_value      varchar(255),
  event_title          varchar(255) not null,
  event_content        clob,
  event_reason         varchar(500),
  ai_summary           clob,
  ai_summary_status    varchar(16),
  first_triggered_at   timestamp default current_timestamp not null,
  last_triggered_at    timestamp default current_timestamp not null,
  confirmed_at         timestamp,
  recovered_at         timestamp,
  closed_at            timestamp,
  created_at           timestamp default current_timestamp not null,
  updated_at           timestamp default current_timestamp not null,
  constraint uk_alert_event_no unique (event_no)
);
create index if not exists idx_alert_event_rule on alert_event (rule_id);
create index if not exists idx_alert_event_object on alert_event (object_id);
create index if not exists idx_alert_event_status on alert_event (event_status);
create index if not exists idx_alert_event_level on alert_event (alert_level);
create index if not exists idx_alert_event_incident on alert_event (incident_id);
create index if not exists idx_alert_event_first_at on alert_event (first_triggered_at);

-- 8. alert_event_handle_log
create table if not exists alert_event_handle_log (
  id              bigint auto_increment primary key,
  event_id        bigint not null,
  action_type     varchar(32) not null,
  before_status   varchar(16),
  after_status    varchar(16),
  operator_name   varchar(64),
  operator_phone  varchar(32),
  action_comment  varchar(1000),
  created_at      timestamp default current_timestamp not null
);
create index if not exists idx_aehl_event on alert_event_handle_log (event_id);

-- 9. alert_notify_log
create table if not exists alert_notify_log (
  id                bigint auto_increment primary key,
  event_id          bigint not null,
  rule_id           bigint not null,
  channel_id        bigint not null,
  channel_type      varchar(32) not null,
  receiver_value    varchar(500),
  notify_title      varchar(255),
  notify_content    clob,
  send_status       varchar(16) default 'PENDING' not null,
  provider_msg_id   varchar(128),
  failure_reason    varchar(1000),
  sent_at           timestamp,
  created_at        timestamp default current_timestamp not null
);
create index if not exists idx_anl_event on alert_notify_log (event_id);
create index if not exists idx_anl_rule on alert_notify_log (rule_id);
create index if not exists idx_anl_channel on alert_notify_log (channel_id);
create index if not exists idx_anl_status on alert_notify_log (send_status);

-- 10. alert_incident
create table if not exists alert_incident (
  id              bigint auto_increment primary key,
  incident_no     varchar(64) not null,
  object_id       bigint not null,
  object_type     varchar(32) not null,
  object_name     varchar(128) not null,
  top_level       varchar(16) not null,
  event_count     int default 1 not null,
  status          varchar(16) default 'OPEN' not null,
  summary         clob,
  first_event_at  timestamp not null,
  last_event_at   timestamp not null,
  closed_at       timestamp,
  created_at      timestamp default current_timestamp not null,
  updated_at      timestamp default current_timestamp not null,
  constraint uk_incident_no unique (incident_no)
);
create index if not exists idx_incident_object on alert_incident (object_id);
create index if not exists idx_incident_status on alert_incident (status);

-- 11. llm_model_config
create table if not exists llm_model_config (
  id           bigint auto_increment primary key,
  config_code  varchar(64) not null,
  config_name  varchar(128) not null,
  provider     varchar(32) not null,
  base_url     varchar(255) not null,
  api_key      varchar(255) not null,
  model_name   varchar(128) not null,
  temperature  decimal(3,2) default 0.20 not null,
  max_tokens   int default 2048 not null,
  is_default   tinyint default 0 not null,
  status       varchar(16) default 'ENABLED' not null,
  description  varchar(500),
  created_at   timestamp default current_timestamp not null,
  updated_at   timestamp default current_timestamp not null,
  constraint uk_llm_code unique (config_code)
);

-- 12. ai_call_log
create table if not exists ai_call_log (
  id                bigint auto_increment primary key,
  scene             varchar(32) not null,
  model_config_id   bigint,
  model_name        varchar(128),
  request_payload   clob,
  response_payload  clob,
  prompt_tokens     int,
  completion_tokens int,
  duration_ms       int,
  status            varchar(16) default 'SUCCESS' not null,
  error_message     varchar(1000),
  created_at        timestamp default current_timestamp not null
);
create index if not exists idx_aicl_scene on ai_call_log (scene);
create index if not exists idx_aicl_status on ai_call_log (status);

-- 13. metric_sample
create table if not exists metric_sample (
  id            bigint auto_increment primary key,
  object_id     bigint not null,
  metric_code   varchar(64) not null,
  metric_value  varchar(64) not null,
  numeric_value decimal(20,4),
  sampled_at    timestamp default current_timestamp not null
);
create index if not exists idx_metric_object_code_time on metric_sample (object_id, metric_code, sampled_at);
