package com.aiops.alert.service.support;

import com.aiops.alert.common.Enums;
import com.aiops.alert.common.MetricCatalog;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertIncident;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.AlertRuleChannelRel;
import com.aiops.alert.entity.AlertRuleCondition;
import com.aiops.alert.entity.AlertRuleObjectRel;
import com.aiops.alert.entity.MetricSample;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertChannelMapper;
import com.aiops.alert.mapper.AlertEventHandleLogMapper;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertIncidentMapper;
import com.aiops.alert.mapper.AlertNotifyLogMapper;
import com.aiops.alert.mapper.AlertRuleChannelRelMapper;
import com.aiops.alert.mapper.AlertRuleConditionMapper;
import com.aiops.alert.mapper.AlertRuleMapper;
import com.aiops.alert.mapper.AlertRuleObjectRelMapper;
import com.aiops.alert.mapper.MetricSampleMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.aiops.alert.service.core.AlertEventService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一键演示数据：清空业务数据并植入一组对象、渠道、规则、并触发几条事件。
 */
@Slf4j
@Service
public class DemoDataService {

    private final MonitorObjectMapper objectMapper;
    private final AlertChannelMapper channelMapper;
    private final AlertRuleMapper ruleMapper;
    private final AlertRuleConditionMapper conditionMapper;
    private final AlertRuleObjectRelMapper objectRelMapper;
    private final AlertRuleChannelRelMapper channelRelMapper;
    private final AlertEventMapper eventMapper;
    private final AlertEventHandleLogMapper handleLogMapper;
    private final AlertNotifyLogMapper notifyLogMapper;
    private final AlertIncidentMapper incidentMapper;
    private final MetricSampleMapper sampleMapper;
    private final AlertEventService eventService;

    public DemoDataService(MonitorObjectMapper objectMapper,
                           AlertChannelMapper channelMapper,
                           AlertRuleMapper ruleMapper,
                           AlertRuleConditionMapper conditionMapper,
                           AlertRuleObjectRelMapper objectRelMapper,
                           AlertRuleChannelRelMapper channelRelMapper,
                           AlertEventMapper eventMapper,
                           AlertEventHandleLogMapper handleLogMapper,
                           AlertNotifyLogMapper notifyLogMapper,
                           AlertIncidentMapper incidentMapper,
                           MetricSampleMapper sampleMapper,
                           AlertEventService eventService) {
        this.objectMapper = objectMapper;
        this.channelMapper = channelMapper;
        this.ruleMapper = ruleMapper;
        this.conditionMapper = conditionMapper;
        this.objectRelMapper = objectRelMapper;
        this.channelRelMapper = channelRelMapper;
        this.eventMapper = eventMapper;
        this.handleLogMapper = handleLogMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.incidentMapper = incidentMapper;
        this.sampleMapper = sampleMapper;
        this.eventService = eventService;
    }

    @Transactional(rollbackFor = Exception.class)
    public String clean() {
        eventMapper.delete(null);
        handleLogMapper.delete(null);
        notifyLogMapper.delete(null);
        incidentMapper.delete(null);
        channelRelMapper.delete(null);
        objectRelMapper.delete(null);
        conditionMapper.delete(null);
        ruleMapper.delete(null);
        channelMapper.delete(null);
        objectMapper.delete(null);
        sampleMapper.delete(null);
        return "清理完成";
    }

    @Transactional(rollbackFor = Exception.class)
    public String seed() {
        clean();

        // 1. 监控对象
        List<MonitorObject> objects = new ArrayList<>();
        objects.add(buildObject("SRV-WEB-01", "prod-web-01", Enums.ObjectType.SERVER,
                "张明", "13800000001", "prod,核心,7x24",
                "{\"ip\":\"10.0.1.21\",\"env\":\"prod\",\"region\":\"north\"}"));
        objects.add(buildObject("SRV-WEB-02", "prod-web-02", Enums.ObjectType.SERVER,
                "张明", "13800000001", "prod,核心",
                "{\"ip\":\"10.0.1.22\",\"env\":\"prod\"}"));
        objects.add(buildObject("DB-MYSQL-MASTER", "生产 MySQL 主库", Enums.ObjectType.DATABASE,
                "李芳", "13800000002", "prod,DB,关键",
                "{\"type\":\"mysql\",\"host\":\"10.0.2.10\",\"port\":3306}"));
        objects.add(buildObject("DB-REDIS-CACHE", "生产 Redis 集群", Enums.ObjectType.DATABASE,
                "李芳", "13800000002", "prod,缓存",
                "{\"type\":\"redis\",\"nodes\":3}"));
        objects.add(buildObject("JOB-SYNC-CUSTOMER", "客户信息同步作业", Enums.ObjectType.SYNC_JOB,
                "王浩", "13800000003", "数仓,T+1",
                "{\"source\":\"crm\",\"target\":\"dwd\"}"));
        objects.add(buildObject("JOB-SYNC-ORDER", "订单同步作业", Enums.ObjectType.SYNC_JOB,
                "王浩", "13800000003", "数仓,实时",
                "{\"source\":\"oms\",\"target\":\"ods\"}"));
        objects.add(buildObject("JOB-DAILY-REPORT", "日报汇总加工任务", Enums.ObjectType.PROCESS_JOB,
                "陈晨", "13800000004", "报表,T+1",
                "{\"platform\":\"airflow\"}"));

        for (MonitorObject o : objects) {
            objectMapper.insert(o);
        }

        // 2. 通知渠道
        AlertChannel wecom = new AlertChannel();
        wecom.setChannelCode("CH-WECOM-OPS");
        wecom.setChannelName("运维值班企微群");
        wecom.setChannelType(Enums.ChannelType.WECOM);
        wecom.setProviderName("企业微信");
        wecom.setStatus(Enums.Status.ENABLED);
        wecom.setPriority(10);
        wecom.setConfigJson("{\"webhook\":\"https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo\",\"mentionedMobileList\":\"13800000001,13800000002\",\"dryRun\":true}");
        wecom.setDescription("dryRun 模式演示，标记成功不真实发送");
        channelMapper.insert(wecom);

        AlertChannel email = new AlertChannel();
        email.setChannelCode("CH-EMAIL-OPS");
        email.setChannelName("运维邮件组");
        email.setChannelType(Enums.ChannelType.EMAIL);
        email.setProviderName("自建 SMTP");
        email.setStatus(Enums.Status.ENABLED);
        email.setPriority(20);
        email.setConfigJson("{\"host\":\"smtp.example.com\",\"port\":465,\"ssl\":true,\"username\":\"alert@example.com\",\"password\":\"demo\",\"defaultReceivers\":\"ops@example.com\",\"dryRun\":true}");
        email.setDescription("dryRun 模式演示，标记成功不真实发送");
        channelMapper.insert(email);

        AlertChannel sms = new AlertChannel();
        sms.setChannelCode("CH-SMS-DEMO");
        sms.setChannelName("短信演示通道");
        sms.setChannelType(Enums.ChannelType.SMS);
        sms.setProviderName("阿里云");
        sms.setStatus(Enums.Status.ENABLED);
        sms.setPriority(30);
        sms.setConfigJson("{\"provider\":\"aliyun\",\"sign\":\"AIOps\",\"template\":\"SMS_DEMO\",\"dryRun\":true,\"defaultReceivers\":\"13800000001\"}");
        sms.setDescription("dryRun 模式演示，标记成功不真实发送");
        channelMapper.insert(sms);

        // 3. 告警规则（每个对象类型至少一条，多个条件示例）
        AlertRule cpuRule = buildRule("RULE-SRV-CPU", "服务器 CPU 高使用率", Enums.ObjectType.SERVER,
                "AND", 3, 5, 10, Enums.AlertLevel.SERIOUS, 100,
                "服务器 CPU 持续高位，影响响应延迟");
        ruleMapper.insert(cpuRule);
        conditionMapper.insert(cond(cpuRule.getId(), 1, "cpu_usage", "CPU 使用率", "GT", "85", "%"));
        bind(cpuRule.getId(), List.of(objects.get(0).getId(), objects.get(1).getId()),
                List.of(wecom.getId(), email.getId()), "ops@example.com");

        AlertRule memRule = buildRule("RULE-SRV-MEM", "服务器内存高占用", Enums.ObjectType.SERVER,
                "AND", 3, 5, 10, Enums.AlertLevel.SERIOUS, 110,
                "内存高占用通常意味着内存泄漏或负载激增");
        ruleMapper.insert(memRule);
        conditionMapper.insert(cond(memRule.getId(), 1, "memory_usage", "内存使用率", "GT", "88", "%"));
        bind(memRule.getId(), List.of(objects.get(0).getId(), objects.get(1).getId()),
                List.of(wecom.getId()), null);

        AlertRule dbRule = buildRule("RULE-DB-LAG", "MySQL 主从延迟", Enums.ObjectType.DATABASE,
                "AND", 2, 5, 30, Enums.AlertLevel.CRITICAL, 90,
                "主从延迟过大可能导致读节点数据落后");
        ruleMapper.insert(dbRule);
        conditionMapper.insert(cond(dbRule.getId(), 1, "replication_lag", "主从延迟", "GT", "300", "秒"));
        bind(dbRule.getId(), List.of(objects.get(2).getId()),
                List.of(wecom.getId(), sms.getId()), "13800000002");

        AlertRule slowRule = buildRule("RULE-DB-SLOW", "MySQL 慢查询激增", Enums.ObjectType.DATABASE,
                "AND", 2, 5, 30, Enums.AlertLevel.SERIOUS, 95,
                "慢查询激增通常预示着锁等待或索引失效");
        ruleMapper.insert(slowRule);
        conditionMapper.insert(cond(slowRule.getId(), 1, "slow_query_per_min", "慢查询/分钟", "GT", "50", "次"));
        bind(slowRule.getId(), List.of(objects.get(2).getId()),
                List.of(wecom.getId()), null);

        AlertRule jobRule = buildRule("RULE-SYNC-FAIL", "数据同步作业失败", Enums.ObjectType.SYNC_JOB,
                "AND", 1, 5, 10, Enums.AlertLevel.CRITICAL, 80,
                "同步作业失败会影响下游数据时效");
        ruleMapper.insert(jobRule);
        conditionMapper.insert(cond(jobRule.getId(), 1, "job_status", "作业执行状态", "EQ", "FAILED", null));
        bind(jobRule.getId(), List.of(objects.get(4).getId(), objects.get(5).getId()),
                List.of(wecom.getId(), email.getId()), "data-team@example.com");

        AlertRule procRule = buildRule("RULE-PROC-DURATION", "数据加工作业超时", Enums.ObjectType.PROCESS_JOB,
                "AND", 1, 10, 30, Enums.AlertLevel.SERIOUS, 105,
                "加工作业超时意味着报表上线延迟");
        ruleMapper.insert(procRule);
        conditionMapper.insert(cond(procRule.getId(), 1, "job_duration", "执行耗时", "GT", "30", "分钟"));
        bind(procRule.getId(), List.of(objects.get(6).getId()),
                List.of(email.getId()), "report-team@example.com");

        // 4. 触发若干历史事件，让看板和列表立刻有内容
        eventService.triggerEvent(cpuRule, objects.get(0), "cpu_usage=92%", "CPU 持续 5 分钟超过 85%");
        eventService.triggerEvent(memRule, objects.get(0), "memory_usage=91%", "内存使用率长时间高位");
        eventService.triggerEvent(dbRule, objects.get(2), "replication_lag=480 秒", "从库 IO 线程延迟严重");
        eventService.triggerEvent(slowRule, objects.get(2), "slow_query_per_min=82", "热点表缺索引导致全表扫描");
        eventService.triggerEvent(jobRule, objects.get(4), "job_status=FAILED", "源表 schema 变更导致同步失败");
        eventService.triggerEvent(procRule, objects.get(6), "job_duration=42 分钟", "上游延迟连锁反应");

        // 5. 历史回填：12 条 7 天内的 AlertEvent + 7 天内 ~100 条/(object,metric) 的 MetricSample
        //    让总览大屏 7 日趋势曲线显著、阈值推荐有真实分位数据、详情页可看到预生成 AI 摘要
        List<AlertRule> allRules = List.of(cpuRule, memRule, dbRule, slowRule, jobRule, procRule);
        seedHistorical(objects, allRules);

        return "演示数据生成完成：%d 个对象，%d 个渠道，%d 条规则，事件已触发"
                .formatted(objects.size(), 3, 6);
    }

    private MonitorObject buildObject(String code, String name, String type,
                                      String owner, String phone, String tags, String ext) {
        MonitorObject o = new MonitorObject();
        o.setObjectCode(code);
        o.setObjectName(name);
        o.setObjectType(type);
        o.setOwnerName(owner);
        o.setOwnerPhone(phone);
        o.setTags(tags);
        o.setStatus(Enums.Status.ENABLED);
        o.setExtConfig(ext);
        return o;
    }

    private AlertRule buildRule(String code, String name, String objectType, String logic,
                                int triggerTimes, int window, int interval, String level,
                                int priority, String desc) {
        AlertRule r = new AlertRule();
        r.setRuleCode(code);
        r.setRuleName(name);
        r.setObjectType(objectType);
        r.setConditionLogic(logic);
        r.setTriggerTimes(triggerTimes);
        r.setTimeWindowMinutes(window);
        r.setMinAlertIntervalMinutes(interval);
        r.setAlertLevel(level);
        r.setRecoverNotify(1);
        r.setRepeatNotify(0);
        r.setStatus(Enums.Status.ENABLED);
        r.setPriority(priority);
        r.setDescription(desc);
        return r;
    }

    private AlertRuleCondition cond(Long ruleId, int order, String code, String name,
                                    String op, String threshold, String unit) {
        AlertRuleCondition c = new AlertRuleCondition();
        c.setRuleId(ruleId);
        c.setConditionOrder(order);
        c.setMetricCode(code);
        c.setMetricName(name);
        c.setCompareOp(op);
        c.setThresholdValue(threshold);
        c.setThresholdUnit(unit);
        return c;
    }

    private void bind(Long ruleId, List<Long> objectIds, List<Long> channelIds, String receiver) {
        for (Long oid : objectIds) {
            AlertRuleObjectRel rel = new AlertRuleObjectRel();
            rel.setRuleId(ruleId);
            rel.setObjectId(oid);
            objectRelMapper.insert(rel);
        }
        for (Long cid : channelIds) {
            AlertRuleChannelRel rel = new AlertRuleChannelRel();
            rel.setRuleId(ruleId);
            rel.setChannelId(cid);
            rel.setReceiverValue(receiver);
            channelRelMapper.insert(rel);
        }
    }

    // ========================================================================
    // 历史回填（spec: demo-readiness-and-ai-observability）
    //
    // 目标：
    //  1. 总览大屏 7 日趋势图四条曲线（总量 / 待处理 / 已恢复 / 紧急）显著有曲线，不再是单日柱
    //  2. 详情页可看到 SUCCESS 状态的预生成 AI 摘要，不需要现场调 LLM
    //  3. 阈值推荐能基于真实分位数据返回 source=HISTORY
    //
    // 策略（确定性，演示效果可控）：
    //  - 12 条 AlertEvent 分散到 day-1 ~ day-7（每天 1-3 条）
    //  - 状态混合：4×RECOVERED + 3×CLOSED + 3×CONFIRMED + 2×PENDING（covers ≥3 状态 + ≥1 RECOVERED）
    //  - 级别混合：4×CRITICAL + 5×SERIOUS + 3×NORMAL（covers ≥2 级别）
    //  - 对象类型：覆盖 SERVER / DATABASE / SYNC_JOB / PROCESS_JOB 4 种
    //  - 3 条 RECOVERED 写入 PRE_GENERATED_SUMMARIES（ai_summary_status=SUCCESS）
    //  - 当下 6 条事件中至少 1 条保持 PENDING（用于演示现场 AI 流式生成）
    //  - MetricSample：每个数值条件型 (object, metric) 写 7 天 100+ 条，P95 > P50
    // ========================================================================

    /** 一日内的固定时刻分布，避免随机带来的"运气演示效果"差异。 */
    private static final LocalTime[] DAILY_SLOTS = new LocalTime[]{
            LocalTime.of(8, 30),
            LocalTime.of(13, 15),
            LocalTime.of(19, 42)
    };

    /** 3 条预生成的 AI 摘要 JSON（ai_summary_status=SUCCESS），按 (规则索引) 选用。 */
    private static final String[] PRE_GENERATED_SUMMARIES = new String[]{
            // 第 0 条：MySQL 主从延迟
            """
            {
              "what": "生产 MySQL 主库主从延迟超过 5 分钟，连续 3 次命中阈值",
              "impact": "读写分离场景下从库查询返回旧数据，订单查询和报表受影响",
              "causes": ["业务高峰期写入激增", "从库 IO 线程被慢 SQL 阻塞", "网络抖动导致 binlog 同步延迟"],
              "actions": ["短期：临时把读流量切回主库", "中期：排查从库慢 SQL 并加索引", "长期：评估迁移到 GTID 模式"]
            }
            """,
            // 第 1 条：服务器 CPU 高
            """
            {
              "what": "prod-web-01 CPU 使用率持续 10 分钟超过 85%，触发 SERIOUS 级告警",
              "impact": "前端响应延迟 P99 上升至 2.4s，网关错误率上升 0.3 个百分点",
              "causes": ["营销活动流量峰值", "Java 应用某接口未走缓存", "近期发布引入了 N+1 查询"],
              "actions": ["立即：限流保护核心接口", "观察：扩容一台 web 实例", "复盘：审查最近 3 次发布的 SQL 查询"]
            }
            """,
            // 第 2 条：同步作业失败
            """
            {
              "what": "客户信息同步作业（JOB-SYNC-CUSTOMER）连续 2 次失败，阻塞当日数仓 T+1 流程",
              "impact": "下游 BI 报表延迟 4 小时，CRM 与数仓客户视图数据不一致",
              "causes": ["源表新增字段导致 schema 不兼容", "同步任务读取超时", "Airflow DAG 重试耗尽"],
              "actions": ["立即：手动补跑当日批次", "短期：补上 schema 变更通知机制", "长期：迁移到 schema-aware 的同步框架"]
            }
            """
    };

    /** 从指定规则的所有数值型条件中取一个用于回填（state 类型跳过）。 */
    private AlertRuleCondition firstNumericCondition(Long ruleId) {
        List<AlertRuleCondition> conds = conditionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertRuleCondition>()
                        .eq(AlertRuleCondition::getRuleId, ruleId)
                        .orderByAsc(AlertRuleCondition::getConditionOrder));
        for (AlertRuleCondition c : conds) {
            String op = c.getCompareOp();
            if ("GT".equals(op) || "GE".equals(op) || "LT".equals(op) || "LE".equals(op)) {
                return c;
            }
        }
        return conds.isEmpty() ? null : conds.get(0);
    }

    /** 入口：12 条历史 event + 全量 metric_sample 7 天分位数据。 */
    private void seedHistorical(List<MonitorObject> objects, List<AlertRule> rules) {
        backfillHistoricalEvents(objects, rules);
        backfillHistoricalMetricSamples(objects, rules);
    }

    /**
     * 12 条历史 AlertEvent 跨 day-1 ~ day-7 分布。
     *
     * 每条事件用 (rule, object, dayOffset, slotIdx) 唯一定位。状态、级别、是否预生成摘要按下表配比。
     */
    private void backfillHistoricalEvents(List<MonitorObject> objects, List<AlertRule> rules) {
        AlertRule cpuRule = rules.get(0);
        AlertRule memRule = rules.get(1);
        AlertRule dbRule = rules.get(2);
        AlertRule slowRule = rules.get(3);
        AlertRule jobRule = rules.get(4);
        AlertRule procRule = rules.get(5);

        MonitorObject web1 = objects.get(0);
        MonitorObject web2 = objects.get(1);
        MonitorObject mysql = objects.get(2);
        MonitorObject syncCustomer = objects.get(4);
        MonitorObject syncOrder = objects.get(5);
        MonitorObject report = objects.get(6);

        // 12 条事件计划：(规则, 对象, dayOffset(1=昨天), slotIdx, 状态, 当前值, 预生成摘要 idx 或 -1)
        Object[][] plan = new Object[][]{
                // 第 1 天前（昨天）：3 条，含一条 RECOVERED 带预生成摘要
                {dbRule,   mysql,        1, 0, Enums.EventStatus.RECOVERED, "replication_lag=520 秒", 0},
                {cpuRule,  web1,         1, 1, Enums.EventStatus.CONFIRMED, "cpu_usage=87%", -1},
                {procRule, report,       1, 2, Enums.EventStatus.RECOVERED, "job_duration=46 分钟", -1},

                // 第 2 天前：2 条
                {jobRule,  syncCustomer, 2, 0, Enums.EventStatus.RECOVERED, "job_status=FAILED", 2},
                {memRule,  web2,         2, 2, Enums.EventStatus.CLOSED, "memory_usage=89%", -1},

                // 第 3 天前：1 条
                {slowRule, mysql,        3, 1, Enums.EventStatus.CLOSED, "slow_query_per_min=78", -1},

                // 第 4 天前：2 条，含一条 RECOVERED 带预生成摘要
                {cpuRule,  web2,         4, 0, Enums.EventStatus.RECOVERED, "cpu_usage=91%", 1},
                {jobRule,  syncOrder,    4, 2, Enums.EventStatus.PENDING, "job_status=FAILED", -1},

                // 第 5 天前：1 条
                {dbRule,   mysql,        5, 1, Enums.EventStatus.CLOSED, "replication_lag=410 秒", -1},

                // 第 6 天前：2 条
                {memRule,  web1,         6, 0, Enums.EventStatus.CONFIRMED, "memory_usage=92%", -1},
                {procRule, report,       6, 2, Enums.EventStatus.PENDING, "job_duration=38 分钟", -1},

                // 第 7 天前：1 条
                {slowRule, mysql,        7, 1, Enums.EventStatus.CONFIRMED, "slow_query_per_min=64", -1}
        };

        for (Object[] row : plan) {
            AlertRule rule = (AlertRule) row[0];
            MonitorObject obj = (MonitorObject) row[1];
            int dayOffset = (int) row[2];
            int slotIdx = (int) row[3];
            String status = (String) row[4];
            String currentValue = (String) row[5];
            int summaryIdx = (int) row[6];

            LocalDateTime triggeredAt = LocalDate.now()
                    .minusDays(dayOffset)
                    .atTime(DAILY_SLOTS[slotIdx]);

            String preSummary = (summaryIdx >= 0 && summaryIdx < PRE_GENERATED_SUMMARIES.length)
                    ? PRE_GENERATED_SUMMARIES[summaryIdx].trim()
                    : null;

            backfillEvent(rule, obj, currentValue, defaultReason(rule, currentValue),
                    triggeredAt, status, preSummary);
        }
    }

    /**
     * 直接 mapper.insert 一条历史 AlertEvent，绕过 AlertEventService.triggerEvent 的 SSE 广播 + 异步 LLM 调用。
     *
     * 显式赋值 first_triggered_at / last_triggered_at / created_at / updated_at，让 dashboard 7 日趋势按真实日期分组。
     */
    private void backfillEvent(AlertRule rule, MonitorObject object,
                                String currentValue, String reason,
                                LocalDateTime triggeredAt, String eventStatus,
                                String preGeneratedSummaryJson) {
        AlertEvent event = new AlertEvent();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        event.setEventNo("ALERT-" + triggeredAt.toLocalDate().format(fmt) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        event.setRuleId(rule.getId());
        event.setObjectId(object.getId());
        event.setObjectType(object.getObjectType());
        event.setObjectName(object.getObjectName());
        event.setMetricCode(rule.getRuleCode());
        event.setMetricName(rule.getRuleName());
        event.setAlertLevel(rule.getAlertLevel());
        event.setEventStatus(eventStatus);
        event.setCurrentValue(currentValue);
        event.setEventTitle("[" + rule.getAlertLevel() + "] " + object.getObjectName() + " · " + rule.getRuleName());
        event.setEventContent(reason);
        event.setEventReason(reason);

        // 状态时间戳
        event.setFirstTriggeredAt(triggeredAt);
        event.setLastTriggeredAt(triggeredAt);
        if (Enums.EventStatus.CONFIRMED.equals(eventStatus)
                || Enums.EventStatus.RECOVERED.equals(eventStatus)
                || Enums.EventStatus.CLOSED.equals(eventStatus)) {
            event.setConfirmedAt(triggeredAt.plusMinutes(8));
        }
        if (Enums.EventStatus.RECOVERED.equals(eventStatus)
                || Enums.EventStatus.CLOSED.equals(eventStatus)) {
            event.setRecoveredAt(triggeredAt.plusMinutes(35));
        }
        if (Enums.EventStatus.CLOSED.equals(eventStatus)) {
            event.setClosedAt(triggeredAt.plusMinutes(60));
        }

        // 预生成 AI 摘要
        if (preGeneratedSummaryJson != null) {
            event.setAiSummary(preGeneratedSummaryJson);
            event.setAiSummaryStatus("SUCCESS");
            event.setAiReasoning("基于历史相似事件特征生成（演示数据预填充）");
        } else {
            event.setAiSummaryStatus("PENDING");
        }

        eventMapper.insert(event);
    }

    /** 用规则的告警级别和当前值生成一段事件原因文案，避免 schema 里 reason 字段空白。 */
    private String defaultReason(AlertRule rule, String currentValue) {
        return rule.getRuleName() + "：" + currentValue + "（连续触发 " + rule.getTriggerTimes() + " 次）";
    }

    /**
     * 为每条数值型规则关联的 (object, metric) 维度写 7 天 ~ 14×7 = 98+ 条 metric_sample。
     *
     * 数值用 baseValue + sigma * sin(t * 0.4) + 噪声，确保 P95 > P50（满足 Property 4）。
     */
    private void backfillHistoricalMetricSamples(List<MonitorObject> objects, List<AlertRule> rules) {
        LocalDateTime now = LocalDateTime.now();
        for (AlertRule rule : rules) {
            AlertRuleCondition cond = firstNumericCondition(rule.getId());
            if (cond == null) continue;
            String op = cond.getCompareOp();
            if (!"GT".equals(op) && !"GE".equals(op) && !"LT".equals(op) && !"LE".equals(op)) {
                continue; // 跳过状态枚举类
            }
            // 找出该规则关联的 object_id
            List<Long> objectIds = objectRelMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertRuleObjectRel>()
                            .eq(AlertRuleObjectRel::getRuleId, rule.getId()))
                    .stream().map(AlertRuleObjectRel::getObjectId).toList();
            for (Long objectId : objectIds) {
                double base = parseBaseValue(cond.getThresholdValue(), cond.getMetricCode());
                double sigma = base * 0.18;  // 让 P95 - P50 显著
                backfillMetricSamples(objectId, cond.getMetricCode(), cond.getThresholdUnit(),
                        base, sigma, now);
            }
        }
    }

    /** 为单个 (object, metric) 写 7 × 14 = 98 条样本。 */
    private void backfillMetricSamples(Long objectId, String metricCode, String unit,
                                        double baseValue, double sigma, LocalDateTime now) {
        List<MetricSample> batch = new ArrayList<>(98);
        for (int dayOffset = 7; dayOffset >= 1; dayOffset--) {
            LocalDateTime dayStart = now.toLocalDate().minusDays(dayOffset).atStartOfDay();
            for (int slot = 0; slot < 14; slot++) {
                LocalDateTime ts = dayStart.plusMinutes(slot * 100L);  // 约 1.67h 一条
                double sineComponent = sigma * Math.sin((dayOffset * 14 + slot) * 0.4);
                double noise = ThreadLocalRandom.current().nextDouble(-sigma / 3, sigma / 3);
                double value = baseValue + sineComponent + noise;

                MetricSample sample = new MetricSample();
                sample.setObjectId(objectId);
                sample.setMetricCode(metricCode);
                sample.setMetricValue(formatNumeric(value, unit));
                sample.setNumericValue(BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP));
                sample.setSampledAt(ts);
                batch.add(sample);
            }
        }
        for (MetricSample s : batch) {
            sampleMapper.insert(s);
        }
    }

    /** 把规则配置的 threshold（字符串）转成基线数值；解析失败按指标编码兜底经验值。 */
    private double parseBaseValue(String thresholdValue, String metricCode) {
        try {
            return Double.parseDouble(thresholdValue);
        } catch (NumberFormatException | NullPointerException e) {
            // 经验兜底
            return switch (metricCode) {
                case "cpu_usage" -> 60.0;
                case "memory_usage" -> 65.0;
                case "replication_lag" -> 180.0;
                case "slow_query_per_min" -> 25.0;
                case "job_duration" -> 18.0;
                default -> 50.0;
            };
        }
    }

    private String formatNumeric(double value, String unit) {
        String body = String.format("%.2f", value);
        return unit == null ? body : body + unit;
    }
}
