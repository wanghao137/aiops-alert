package com.aiops.alert.service.engine;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.AlertRuleCondition;
import com.aiops.alert.entity.AlertRuleObjectRel;
import com.aiops.alert.entity.MetricSample;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertRuleConditionMapper;
import com.aiops.alert.mapper.AlertRuleMapper;
import com.aiops.alert.mapper.AlertRuleObjectRelMapper;
import com.aiops.alert.mapper.MetricSampleMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.aiops.alert.service.core.AlertEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 规则引擎：定时拉取启用规则，评估每条规则下的对象指标。
 *
 * 评估方式：
 *  - 取最近 timeWindowMinutes 内的 metric_sample
 *  - 对 NUMERIC：用 numeric_value 做比较
 *  - 对 ENUM：用 metric_value 字符串比较（FAILED/OFFLINE 视为命中）
 *  - 当连续触发次数 ≥ triggerTimes 时，且距上次同 ruleId+objectId 事件超过 minAlertIntervalMinutes，触发新事件
 *  - 多条件按 AND/OR 组合
 */
@Slf4j
@Service
public class AlertEngine {

    private final AlertRuleMapper ruleMapper;
    private final AlertRuleConditionMapper conditionMapper;
    private final AlertRuleObjectRelMapper objectRelMapper;
    private final MonitorObjectMapper objectMapper;
    private final MetricSampleMapper sampleMapper;
    private final AlertEventMapper eventMapper;
    private final AlertEventService eventService;

    @Value("${aiops.engine.enabled:true}")
    private boolean enabled;

    public AlertEngine(AlertRuleMapper ruleMapper,
                       AlertRuleConditionMapper conditionMapper,
                       AlertRuleObjectRelMapper objectRelMapper,
                       MonitorObjectMapper objectMapper,
                       MetricSampleMapper sampleMapper,
                       AlertEventMapper eventMapper,
                       @Lazy AlertEventService eventService) {
        this.ruleMapper = ruleMapper;
        this.conditionMapper = conditionMapper;
        this.objectRelMapper = objectRelMapper;
        this.objectMapper = objectMapper;
        this.sampleMapper = sampleMapper;
        this.eventMapper = eventMapper;
        this.eventService = eventService;
    }

    @Scheduled(fixedDelayString = "${aiops.engine.interval-seconds:30}000",
               initialDelay = 20_000)
    public void evaluateAll() {
        if (!enabled) return;

        List<AlertRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getStatus, Enums.Status.ENABLED));
        if (rules.isEmpty()) return;

        List<Long> ruleIds = rules.stream().map(AlertRule::getId).toList();

        // 一次拉取所有条件 / 对象关联
        Map<Long, List<AlertRuleCondition>> condMap = conditionMapper.selectList(
                new LambdaQueryWrapper<AlertRuleCondition>()
                        .in(AlertRuleCondition::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(AlertRuleCondition::getRuleId));

        Map<Long, List<Long>> ruleObjectMap = objectRelMapper.selectList(
                new LambdaQueryWrapper<AlertRuleObjectRel>()
                        .in(AlertRuleObjectRel::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(
                        AlertRuleObjectRel::getRuleId,
                        Collectors.mapping(AlertRuleObjectRel::getObjectId, Collectors.toList())));

        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule,
                        condMap.getOrDefault(rule.getId(), List.of()),
                        ruleObjectMap.getOrDefault(rule.getId(), List.of()));
            } catch (Exception e) {
                log.warn("evaluate rule {} failed: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule, List<AlertRuleCondition> conditions, List<Long> objectIds) {
        if (conditions.isEmpty() || objectIds.isEmpty()) return;
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(
                Math.max(1, rule.getTimeWindowMinutes() == null ? 5 : rule.getTimeWindowMinutes()));

        List<MonitorObject> objects = objectMapper.selectBatchIds(objectIds).stream()
                .filter(o -> Enums.Status.ENABLED.equals(o.getStatus()))
                .toList();
        if (objects.isEmpty()) return;

        for (MonitorObject object : objects) {
            // 静默期检查
            if (inSilencePeriod(rule, object)) continue;

            EvaluationResult ev = evaluateObject(rule, conditions, object, windowStart);
            if (ev.matched && ev.consecutiveHits >= Math.max(1, rule.getTriggerTimes() == null ? 1 : rule.getTriggerTimes())) {
                eventService.triggerEvent(rule, object, ev.currentValue,
                        "规则引擎自动触发：" + ev.summary);
            }
        }
    }

    private boolean inSilencePeriod(AlertRule rule, MonitorObject object) {
        Integer minInterval = rule.getMinAlertIntervalMinutes();
        if (minInterval == null || minInterval <= 0) return false;
        LocalDateTime since = LocalDateTime.now().minusMinutes(minInterval);
        Long count = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getRuleId, rule.getId())
                .eq(AlertEvent::getObjectId, object.getId())
                .ge(AlertEvent::getFirstTriggeredAt, since));
        return count != null && count > 0;
    }

    private static class EvaluationResult {
        boolean matched;
        int consecutiveHits;
        String currentValue;
        String summary;
    }

    private EvaluationResult evaluateObject(AlertRule rule, List<AlertRuleCondition> conditions,
                                            MonitorObject object, LocalDateTime windowStart) {
        EvaluationResult r = new EvaluationResult();
        // 取每个 metricCode 在窗口内的所有样本
        List<String> metrics = conditions.stream().map(AlertRuleCondition::getMetricCode).distinct().toList();
        Map<String, List<MetricSample>> metricSamples = new HashMap<>();
        for (String m : metrics) {
            List<MetricSample> samples = sampleMapper.selectList(new LambdaQueryWrapper<MetricSample>()
                    .eq(MetricSample::getObjectId, object.getId())
                    .eq(MetricSample::getMetricCode, m)
                    .ge(MetricSample::getSampledAt, windowStart)
                    .orderByDesc(MetricSample::getSampledAt));
            metricSamples.put(m, samples);
        }

        // 每个条件按"最近 N 个样本是否连续命中"评估
        // triggerTimes 取规则上的；这里只算"连续命中次数"，由调用方判断是否达到阈值
        boolean useAnd = !"OR".equalsIgnoreCase(rule.getConditionLogic());
        int minConsecutive = Integer.MAX_VALUE; // 多条件下，AND 用最小命中次数；OR 用最大
        int maxConsecutive = 0;
        StringBuilder summary = new StringBuilder();

        boolean allMatched = true;
        boolean anyMatched = false;

        String currentValue = null;
        for (AlertRuleCondition cond : conditions) {
            List<MetricSample> samples = metricSamples.getOrDefault(cond.getMetricCode(), List.of());
            int hits = 0;
            String latestValue = null;
            for (MetricSample s : samples) {
                if (latestValue == null) {
                    latestValue = displayValue(s);
                }
                if (matchSample(cond, s)) {
                    hits++;
                } else {
                    break; // 只看连续命中
                }
            }
            if (latestValue != null && currentValue == null) {
                currentValue = cond.getMetricName() + "=" + latestValue;
            }
            if (hits > 0) {
                summary.append(cond.getMetricName()).append("命中 ").append(hits).append(" 次; ");
                anyMatched = true;
                maxConsecutive = Math.max(maxConsecutive, hits);
                minConsecutive = Math.min(minConsecutive, hits);
            } else {
                allMatched = false;
                minConsecutive = 0;
            }
        }

        if (useAnd) {
            r.matched = allMatched;
            r.consecutiveHits = (allMatched && minConsecutive != Integer.MAX_VALUE) ? minConsecutive : 0;
        } else {
            r.matched = anyMatched;
            r.consecutiveHits = maxConsecutive;
        }
        r.currentValue = currentValue;
        r.summary = summary.length() == 0 ? "" : summary.toString();
        return r;
    }

    private String displayValue(MetricSample s) {
        if (s.getNumericValue() != null) {
            return s.getNumericValue().stripTrailingZeros().toPlainString();
        }
        return StrUtil.blankToDefault(s.getMetricValue(), "");
    }

    private boolean matchSample(AlertRuleCondition cond, MetricSample s) {
        String op = cond.getCompareOp();
        if (StrUtil.isBlank(op)) return false;

        if ("OFFLINE".equalsIgnoreCase(op)) {
            return "OFFLINE".equalsIgnoreCase(s.getMetricValue());
        }
        if ("FAILED".equalsIgnoreCase(op)) {
            return "FAILED".equalsIgnoreCase(s.getMetricValue())
                    || "DOWN".equalsIgnoreCase(s.getMetricValue())
                    || "FAIL".equalsIgnoreCase(s.getMetricValue())
                    || "EMPTY".equalsIgnoreCase(s.getMetricValue());
        }
        if ("TIMEOUT".equalsIgnoreCase(op)) {
            return "TIMEOUT".equalsIgnoreCase(s.getMetricValue());
        }

        // 比较 ENUM
        if (s.getNumericValue() == null) {
            String left = StrUtil.nullToEmpty(s.getMetricValue());
            String right = StrUtil.nullToEmpty(cond.getThresholdValue());
            return switch (op) {
                case "EQ" -> left.equalsIgnoreCase(right);
                case "NE" -> !left.equalsIgnoreCase(right);
                default -> false;
            };
        }

        // 比较 NUMERIC
        BigDecimal left = s.getNumericValue();
        BigDecimal right;
        try {
            right = new BigDecimal(StrUtil.nullToEmpty(cond.getThresholdValue()));
        } catch (NumberFormatException e) {
            return false;
        }
        int cmp = left.compareTo(right);
        return switch (op) {
            case "GT" -> cmp > 0;
            case "GE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LE" -> cmp <= 0;
            case "EQ" -> cmp == 0;
            case "NE" -> cmp != 0;
            default -> false;
        };
    }
}
