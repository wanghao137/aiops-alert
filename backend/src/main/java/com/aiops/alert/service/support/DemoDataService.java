package com.aiops.alert.service.support;

import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.AlertRuleChannelRel;
import com.aiops.alert.entity.AlertRuleCondition;
import com.aiops.alert.entity.AlertRuleObjectRel;
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
import java.util.ArrayList;
import java.util.List;
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
        wecom.setConfigJson("{\"webhook\":\"https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo\",\"mentionedMobileList\":\"13800000001,13800000002\"}");
        wecom.setDescription("演示用，webhook 为占位地址，会发送失败但流程完整");
        channelMapper.insert(wecom);

        AlertChannel email = new AlertChannel();
        email.setChannelCode("CH-EMAIL-OPS");
        email.setChannelName("运维邮件组");
        email.setChannelType(Enums.ChannelType.EMAIL);
        email.setProviderName("自建 SMTP");
        email.setStatus(Enums.Status.ENABLED);
        email.setPriority(20);
        email.setConfigJson("{\"host\":\"smtp.example.com\",\"port\":465,\"ssl\":true,\"username\":\"alert@example.com\",\"password\":\"demo\",\"defaultReceivers\":\"ops@example.com\"}");
        email.setDescription("演示用，发送会失败但流程完整");
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
}
