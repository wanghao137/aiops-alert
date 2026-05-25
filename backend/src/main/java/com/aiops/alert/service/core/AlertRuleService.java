package com.aiops.alert.service.core;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.AlertRuleChannelBindingDto;
import com.aiops.alert.dto.AlertRuleConditionDto;
import com.aiops.alert.dto.AlertRuleRequest;
import com.aiops.alert.dto.AlertRuleResponse;
import com.aiops.alert.dto.AlertRuleStatsResponse;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.AlertRuleChannelRel;
import com.aiops.alert.entity.AlertRuleCondition;
import com.aiops.alert.entity.AlertRuleObjectRel;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertChannelMapper;
import com.aiops.alert.mapper.AlertRuleChannelRelMapper;
import com.aiops.alert.mapper.AlertRuleConditionMapper;
import com.aiops.alert.mapper.AlertRuleMapper;
import com.aiops.alert.mapper.AlertRuleObjectRelMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警规则服务。
 *
 * 维护规则主表 + 多条件 + 对象关联 + 渠道关联 4 张表，更新时全量替换关联表。
 */
@Service
public class AlertRuleService {

    private static final List<String> LEVELS = List.of(
            Enums.AlertLevel.NOTICE, Enums.AlertLevel.NORMAL,
            Enums.AlertLevel.SERIOUS, Enums.AlertLevel.CRITICAL);

    private final AlertRuleMapper ruleMapper;
    private final AlertRuleConditionMapper conditionMapper;
    private final AlertRuleObjectRelMapper objectRelMapper;
    private final AlertRuleChannelRelMapper channelRelMapper;
    private final MonitorObjectMapper objectMapper;
    private final AlertChannelMapper channelMapper;
    private final MetricCatalogService metricCatalog;

    public AlertRuleService(AlertRuleMapper ruleMapper,
                            AlertRuleConditionMapper conditionMapper,
                            AlertRuleObjectRelMapper objectRelMapper,
                            AlertRuleChannelRelMapper channelRelMapper,
                            MonitorObjectMapper objectMapper,
                            AlertChannelMapper channelMapper,
                            MetricCatalogService metricCatalog) {
        this.ruleMapper = ruleMapper;
        this.conditionMapper = conditionMapper;
        this.objectRelMapper = objectRelMapper;
        this.channelRelMapper = channelRelMapper;
        this.objectMapper = objectMapper;
        this.channelMapper = channelMapper;
        this.metricCatalog = metricCatalog;
    }

    public List<AlertRuleResponse> list(String objectType, String alertLevel, String status, String keyword) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<AlertRule>()
                .eq(StrUtil.isNotBlank(objectType), AlertRule::getObjectType, objectType)
                .eq(StrUtil.isNotBlank(alertLevel), AlertRule::getAlertLevel, alertLevel)
                .eq(StrUtil.isNotBlank(status), AlertRule::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(AlertRule::getRuleName, keyword)
                        .or().like(AlertRule::getRuleCode, keyword)
                        .or().like(AlertRule::getDescription, keyword))
                .orderByAsc(AlertRule::getPriority)
                .orderByDesc(AlertRule::getUpdatedAt);
        List<AlertRule> rules = ruleMapper.selectList(wrapper);
        return rules.stream().map(r -> toResponse(r, false)).collect(Collectors.toList());
    }

    public AlertRuleResponse get(Long id) {
        AlertRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("规则不存在");
        }
        return toResponse(rule, true);
    }

    @Transactional
    public AlertRuleResponse save(AlertRuleRequest request) {
        validateRequest(request);

        AlertRule entity;
        if (request.getId() == null) {
            entity = new AlertRule();
        } else {
            entity = ruleMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException("规则不存在");
            }
        }

        String code = StrUtil.isNotBlank(request.getRuleCode())
                ? request.getRuleCode().trim()
                : "RULE-" + shortId();
        if (entity.getId() == null || !code.equals(entity.getRuleCode())) {
            Long count = ruleMapper.selectCount(new LambdaQueryWrapper<AlertRule>()
                    .eq(AlertRule::getRuleCode, code)
                    .ne(entity.getId() != null, AlertRule::getId, entity.getId()));
            if (count != null && count > 0) {
                throw new BizException("规则编码已存在：" + code);
            }
        }

        entity.setRuleCode(code);
        entity.setRuleName(request.getRuleName().trim());
        entity.setObjectType(request.getObjectType());
        entity.setConditionLogic(StrUtil.blankToDefault(request.getConditionLogic(), "AND"));
        entity.setTriggerTimes(defaultIf(request.getTriggerTimes(), 1));
        entity.setTimeWindowMinutes(defaultIf(request.getTimeWindowMinutes(), 5));
        entity.setMinAlertIntervalMinutes(defaultIf(request.getMinAlertIntervalMinutes(), 30));
        entity.setAlertLevel(request.getAlertLevel());
        entity.setRecoverNotify(Boolean.TRUE.equals(request.getRecoverNotify()) ? 1 : 0);
        entity.setRepeatNotify(Boolean.TRUE.equals(request.getRepeatNotify()) ? 1 : 0);
        entity.setStatus(StrUtil.blankToDefault(request.getStatus(), Enums.Status.ENABLED));
        entity.setPriority(defaultIf(request.getPriority(), 100));
        entity.setNotifyTitleTemplate(request.getNotifyTitleTemplate());
        entity.setNotifyContentTemplate(request.getNotifyContentTemplate());
        entity.setDescription(request.getDescription());

        if (entity.getId() == null) {
            ruleMapper.insert(entity);
        } else {
            ruleMapper.updateById(entity);
        }

        replaceConditions(entity.getId(), request.getConditions());
        replaceObjects(entity.getId(), request.getObjectIds());
        replaceChannels(entity.getId(), request.getChannelBindings());

        return toResponse(entity, true);
    }

    @Transactional
    public AlertRuleResponse toggle(Long id) {
        AlertRule entity = ruleMapper.selectById(id);
        if (entity == null) {
            throw new BizException("规则不存在");
        }
        entity.setStatus(Enums.Status.ENABLED.equals(entity.getStatus())
                ? Enums.Status.DISABLED : Enums.Status.ENABLED);
        ruleMapper.updateById(entity);
        return toResponse(entity, true);
    }

    @Transactional
    public void delete(Long id) {
        AlertRule entity = ruleMapper.selectById(id);
        if (entity == null) {
            return;
        }
        ruleMapper.deleteById(id);
        conditionMapper.delete(new LambdaQueryWrapper<AlertRuleCondition>()
                .eq(AlertRuleCondition::getRuleId, id));
        objectRelMapper.delete(new LambdaQueryWrapper<AlertRuleObjectRel>()
                .eq(AlertRuleObjectRel::getRuleId, id));
        channelRelMapper.delete(new LambdaQueryWrapper<AlertRuleChannelRel>()
                .eq(AlertRuleChannelRel::getRuleId, id));
    }

    public AlertRuleStatsResponse stats() {
        long total = ruleMapper.selectCount(null);
        long enabled = ruleMapper.selectCount(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getStatus, Enums.Status.ENABLED));

        List<AlertRule> all = ruleMapper.selectList(null);
        Map<String, Long> levelMap = all.stream()
                .collect(Collectors.groupingBy(AlertRule::getAlertLevel, Collectors.counting()));
        Map<String, Long> typeMap = all.stream()
                .collect(Collectors.groupingBy(AlertRule::getObjectType, Collectors.counting()));

        List<AlertRuleStatsResponse.LevelStat> byLevel = new ArrayList<>();
        for (String level : LEVELS) {
            byLevel.add(AlertRuleStatsResponse.LevelStat.builder()
                    .alertLevel(level)
                    .alertLevelName(levelName(level))
                    .total(levelMap.getOrDefault(level, 0L))
                    .build());
        }
        List<AlertRuleStatsResponse.TypeStat> byType = new ArrayList<>();
        for (String type : Enums.ObjectType.ALL) {
            byType.add(AlertRuleStatsResponse.TypeStat.builder()
                    .objectType(type)
                    .objectTypeName(objectTypeName(type))
                    .total(typeMap.getOrDefault(type, 0L))
                    .build());
        }
        return AlertRuleStatsResponse.builder()
                .total(total).enabled(enabled)
                .byLevel(byLevel).byType(byType)
                .build();
    }

    // ---------------- 内部 ----------------

    private void validateRequest(AlertRuleRequest request) {
        if (!Enums.ObjectType.isValid(request.getObjectType())) {
            throw new BizException("对象类型不合法：" + request.getObjectType());
        }
        if (!LEVELS.contains(request.getAlertLevel())) {
            throw new BizException("告警级别不合法：" + request.getAlertLevel());
        }
        // 校验每个条件的 metricCode 是否在该对象类型的字典里
        for (AlertRuleConditionDto c : request.getConditions()) {
            if (metricCatalog.findMetric(request.getObjectType(), c.getMetricCode()) == null) {
                throw new BizException("指标 " + c.getMetricCode() + " 不属于对象类型 " + request.getObjectType());
            }
        }
        // 校验对象都属于该对象类型
        List<MonitorObject> objects = objectMapper.selectBatchIds(request.getObjectIds());
        for (MonitorObject o : objects) {
            if (!request.getObjectType().equals(o.getObjectType())) {
                throw new BizException("对象 " + o.getObjectName() + " 类型为 "
                        + o.getObjectType() + "，与规则对象类型 " + request.getObjectType() + " 不匹配");
            }
        }
        if (objects.size() != request.getObjectIds().size()) {
            throw new BizException("有对象不存在或已被删除");
        }
        // 校验渠道都存在且启用
        if (request.getChannelBindings() != null && !request.getChannelBindings().isEmpty()) {
            Set<Long> channelIds = request.getChannelBindings().stream()
                    .map(AlertRuleChannelBindingDto::getChannelId)
                    .collect(Collectors.toCollection(HashSet::new));
            if (channelIds.size() != request.getChannelBindings().size()) {
                throw new BizException("同一渠道不能重复绑定");
            }
            List<AlertChannel> channels = channelMapper.selectBatchIds(channelIds);
            if (channels.size() != channelIds.size()) {
                throw new BizException("有渠道不存在或已被删除");
            }
        }
    }

    private void replaceConditions(Long ruleId, List<AlertRuleConditionDto> conditions) {
        conditionMapper.delete(new LambdaQueryWrapper<AlertRuleCondition>()
                .eq(AlertRuleCondition::getRuleId, ruleId));
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        int order = 1;
        for (AlertRuleConditionDto c : conditions) {
            AlertRuleCondition entity = new AlertRuleCondition();
            entity.setRuleId(ruleId);
            entity.setConditionOrder(c.getConditionOrder() == null ? order++ : c.getConditionOrder());
            entity.setMetricCode(c.getMetricCode());
            entity.setMetricName(c.getMetricName());
            entity.setCompareOp(c.getCompareOp());
            entity.setThresholdValue(c.getThresholdValue());
            entity.setThresholdUnit(c.getThresholdUnit());
            conditionMapper.insert(entity);
        }
    }

    private void replaceObjects(Long ruleId, List<Long> objectIds) {
        objectRelMapper.delete(new LambdaQueryWrapper<AlertRuleObjectRel>()
                .eq(AlertRuleObjectRel::getRuleId, ruleId));
        if (objectIds == null) {
            return;
        }
        for (Long objectId : objectIds) {
            AlertRuleObjectRel rel = new AlertRuleObjectRel();
            rel.setRuleId(ruleId);
            rel.setObjectId(objectId);
            objectRelMapper.insert(rel);
        }
    }

    private void replaceChannels(Long ruleId, List<AlertRuleChannelBindingDto> bindings) {
        channelRelMapper.delete(new LambdaQueryWrapper<AlertRuleChannelRel>()
                .eq(AlertRuleChannelRel::getRuleId, ruleId));
        if (bindings == null) {
            return;
        }
        for (AlertRuleChannelBindingDto b : bindings) {
            AlertRuleChannelRel rel = new AlertRuleChannelRel();
            rel.setRuleId(ruleId);
            rel.setChannelId(b.getChannelId());
            rel.setReceiverValue(b.getReceiverValue());
            rel.setTemplateCode(b.getTemplateCode());
            channelRelMapper.insert(rel);
        }
    }

    private AlertRuleResponse toResponse(AlertRule entity, boolean detail) {
        List<AlertRuleConditionDto> conditions = listConditions(entity.getId());
        List<Long> objectIds = listObjectIds(entity.getId());
        List<AlertRuleChannelBindingDto> bindings = listBindings(entity.getId());

        List<AlertRuleResponse.ObjectBrief> objects = Collections.emptyList();
        List<AlertRuleResponse.ChannelBrief> channels = Collections.emptyList();
        if (detail) {
            if (!objectIds.isEmpty()) {
                List<MonitorObject> objs = objectMapper.selectBatchIds(objectIds);
                objects = objs.stream().map(o -> AlertRuleResponse.ObjectBrief.builder()
                        .id(o.getId())
                        .objectName(o.getObjectName())
                        .objectCode(o.getObjectCode())
                        .objectType(o.getObjectType())
                        .status(o.getStatus())
                        .build()).collect(Collectors.toList());
            }
            if (!bindings.isEmpty()) {
                List<Long> channelIds = bindings.stream()
                        .map(AlertRuleChannelBindingDto::getChannelId).collect(Collectors.toList());
                List<AlertChannel> chs = channelMapper.selectBatchIds(channelIds);
                Map<Long, AlertChannel> chMap = chs.stream()
                        .collect(Collectors.toMap(AlertChannel::getId, c -> c));
                channels = bindings.stream().map(b -> {
                    AlertChannel c = chMap.get(b.getChannelId());
                    if (c == null) return null;
                    return AlertRuleResponse.ChannelBrief.builder()
                            .id(c.getId())
                            .channelName(c.getChannelName())
                            .channelType(c.getChannelType())
                            .channelTypeName(channelTypeName(c.getChannelType()))
                            .status(c.getStatus())
                            .receiverValue(b.getReceiverValue())
                            .build();
                }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
            }
        }

        return AlertRuleResponse.builder()
                .id(entity.getId())
                .ruleCode(entity.getRuleCode())
                .ruleName(entity.getRuleName())
                .objectType(entity.getObjectType())
                .objectTypeName(objectTypeName(entity.getObjectType()))
                .conditionLogic(entity.getConditionLogic())
                .triggerTimes(entity.getTriggerTimes())
                .timeWindowMinutes(entity.getTimeWindowMinutes())
                .minAlertIntervalMinutes(entity.getMinAlertIntervalMinutes())
                .alertLevel(entity.getAlertLevel())
                .alertLevelName(levelName(entity.getAlertLevel()))
                .recoverNotify(toBool(entity.getRecoverNotify()))
                .repeatNotify(toBool(entity.getRepeatNotify()))
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .notifyTitleTemplate(entity.getNotifyTitleTemplate())
                .notifyContentTemplate(entity.getNotifyContentTemplate())
                .description(entity.getDescription())
                .conditions(conditions)
                .objectIds(objectIds)
                .objects(objects)
                .channelBindings(bindings)
                .channels(channels)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<AlertRuleConditionDto> listConditions(Long ruleId) {
        return conditionMapper.selectList(new LambdaQueryWrapper<AlertRuleCondition>()
                        .eq(AlertRuleCondition::getRuleId, ruleId)
                        .orderByAsc(AlertRuleCondition::getConditionOrder))
                .stream().map(c -> {
                    AlertRuleConditionDto dto = new AlertRuleConditionDto();
                    dto.setId(c.getId());
                    dto.setConditionOrder(c.getConditionOrder());
                    dto.setMetricCode(c.getMetricCode());
                    dto.setMetricName(c.getMetricName());
                    dto.setCompareOp(c.getCompareOp());
                    dto.setThresholdValue(c.getThresholdValue());
                    dto.setThresholdUnit(c.getThresholdUnit());
                    return dto;
                }).collect(Collectors.toList());
    }

    private List<Long> listObjectIds(Long ruleId) {
        return objectRelMapper.selectList(new LambdaQueryWrapper<AlertRuleObjectRel>()
                        .eq(AlertRuleObjectRel::getRuleId, ruleId))
                .stream().map(AlertRuleObjectRel::getObjectId)
                .collect(Collectors.toList());
    }

    private List<AlertRuleChannelBindingDto> listBindings(Long ruleId) {
        return channelRelMapper.selectList(new LambdaQueryWrapper<AlertRuleChannelRel>()
                        .eq(AlertRuleChannelRel::getRuleId, ruleId))
                .stream().map(r -> {
                    AlertRuleChannelBindingDto dto = new AlertRuleChannelBindingDto();
                    dto.setChannelId(r.getChannelId());
                    dto.setReceiverValue(r.getReceiverValue());
                    dto.setTemplateCode(r.getTemplateCode());
                    return dto;
                }).collect(Collectors.toList());
    }

    private Boolean toBool(Integer v) {
        return v != null && v == 1;
    }

    private Integer defaultIf(Integer value, int dft) {
        return value == null ? dft : value;
    }

    private String levelName(String level) {
        if (level == null) return "";
        return switch (level) {
            case "NOTICE" -> "提示";
            case "NORMAL" -> "一般";
            case "SERIOUS" -> "严重";
            case "CRITICAL" -> "紧急";
            default -> level;
        };
    }

    private String objectTypeName(String type) {
        if (type == null) return "";
        return switch (type) {
            case "SERVER" -> "服务器";
            case "DATABASE" -> "数据库";
            case "SYNC_JOB" -> "数据同步作业";
            case "PROCESS_JOB" -> "数据加工作业";
            default -> type;
        };
    }

    private String channelTypeName(String type) {
        if (type == null) return "";
        return switch (type) {
            case "WECOM" -> "企业微信";
            case "EMAIL" -> "邮件";
            case "SMS" -> "短信";
            default -> type;
        };
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
