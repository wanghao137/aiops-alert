package com.aiops.alert.service.core;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.AlertEventActionRequest;
import com.aiops.alert.dto.AlertEventHandleLogResponse;
import com.aiops.alert.dto.AlertEventResponse;
import com.aiops.alert.dto.AlertEventTestRequest;
import com.aiops.alert.dto.AlertNotifyLogResponse;
import com.aiops.alert.dto.AlertNotifyRetryRequest;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertEventHandleLog;
import com.aiops.alert.entity.AlertIncident;
import com.aiops.alert.entity.AlertNotifyLog;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.AlertRuleChannelRel;
import com.aiops.alert.entity.AlertRuleObjectRel;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertChannelMapper;
import com.aiops.alert.mapper.AlertEventHandleLogMapper;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertNotifyLogMapper;
import com.aiops.alert.mapper.AlertRuleChannelRelMapper;
import com.aiops.alert.mapper.AlertRuleMapper;
import com.aiops.alert.mapper.AlertRuleObjectRelMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.aiops.alert.service.ai.EventSummaryService;
import com.aiops.alert.service.engine.IncidentMerger;
import com.aiops.alert.service.notify.AlertNotifyService;
import com.aiops.alert.service.stream.AlertStreamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AlertEventService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AlertEventMapper eventMapper;
    private final AlertEventHandleLogMapper handleLogMapper;
    private final AlertNotifyLogMapper notifyLogMapper;
    private final AlertRuleMapper ruleMapper;
    private final AlertRuleObjectRelMapper ruleObjectRelMapper;
    private final AlertRuleChannelRelMapper ruleChannelRelMapper;
    private final MonitorObjectMapper objectMapper;
    private final AlertChannelMapper channelMapper;
    private final IncidentMerger incidentMerger;
    private final AlertNotifyService notifyService;
    private final AlertStreamService streamService;
    private final EventSummaryService summaryService;

    public AlertEventService(AlertEventMapper eventMapper,
                             AlertEventHandleLogMapper handleLogMapper,
                             AlertNotifyLogMapper notifyLogMapper,
                             AlertRuleMapper ruleMapper,
                             AlertRuleObjectRelMapper ruleObjectRelMapper,
                             AlertRuleChannelRelMapper ruleChannelRelMapper,
                             MonitorObjectMapper objectMapper,
                             AlertChannelMapper channelMapper,
                             IncidentMerger incidentMerger,
                             AlertNotifyService notifyService,
                             AlertStreamService streamService,
                             EventSummaryService summaryService) {
        this.eventMapper = eventMapper;
        this.handleLogMapper = handleLogMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.ruleMapper = ruleMapper;
        this.ruleObjectRelMapper = ruleObjectRelMapper;
        this.ruleChannelRelMapper = ruleChannelRelMapper;
        this.objectMapper = objectMapper;
        this.channelMapper = channelMapper;
        this.incidentMerger = incidentMerger;
        this.notifyService = notifyService;
        this.streamService = streamService;
        this.summaryService = summaryService;
    }

    public List<AlertEventResponse> list(String objectType, String alertLevel,
                                         String eventStatus, String keyword, Integer limit) {
        LambdaQueryWrapper<AlertEvent> w = new LambdaQueryWrapper<AlertEvent>()
                .eq(StrUtil.isNotBlank(objectType), AlertEvent::getObjectType, objectType)
                .eq(StrUtil.isNotBlank(alertLevel), AlertEvent::getAlertLevel, alertLevel)
                .eq(StrUtil.isNotBlank(eventStatus), AlertEvent::getEventStatus, eventStatus)
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(AlertEvent::getObjectName, keyword)
                        .or().like(AlertEvent::getEventTitle, keyword)
                        .or().like(AlertEvent::getEventNo, keyword))
                .orderByDesc(AlertEvent::getLastTriggeredAt)
                .orderByDesc(AlertEvent::getId);
        if (limit != null && limit > 0) {
            w.last("limit " + limit);
        }
        return eventMapper.selectList(w).stream()
                .map(e -> toResponse(e, false))
                .collect(Collectors.toList());
    }

    public AlertEventResponse get(Long id) {
        AlertEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException("告警事件不存在");
        }
        return toResponse(event, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertEventResponse handle(AlertEventActionRequest request) {
        AlertEvent event = eventMapper.selectById(request.getEventId());
        if (event == null) {
            throw new BizException("告警事件不存在");
        }
        String before = event.getEventStatus();
        String after = mapActionToStatus(request.getActionType(), before);
        LocalDateTime now = LocalDateTime.now();
        if (!StrUtil.equals(before, after)) {
            event.setEventStatus(after);
            switch (after) {
                case "CONFIRMED" -> event.setConfirmedAt(now);
                case "RECOVERED" -> event.setRecoveredAt(now);
                case "CLOSED" -> event.setClosedAt(now);
                default -> {}
            }
            eventMapper.updateById(event);
            // 如果同 incident 的事件全部 RECOVERED/CLOSED，关闭 incident
            if (event.getIncidentId() != null) {
                List<AlertEvent> sameIncident = eventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                        .eq(AlertEvent::getIncidentId, event.getIncidentId()));
                incidentMerger.closeIfAllRecovered(event.getIncidentId(), sameIncident);
            }
        }

        AlertEventHandleLog log = new AlertEventHandleLog();
        log.setEventId(event.getId());
        log.setActionType(request.getActionType());
        log.setBeforeStatus(before);
        log.setAfterStatus(after);
        log.setOperatorName(StrUtil.blankToDefault(request.getOperatorName(), "admin"));
        log.setOperatorPhone(request.getOperatorPhone());
        log.setActionComment(request.getActionComment());
        handleLogMapper.insert(log);

        AlertEventResponse resp = toResponse(event, true);
        streamService.broadcast("event-updated", resp);
        return resp;
    }

    public List<AlertNotifyLogResponse> notifyLogs(Long eventId) {
        return notifyLogMapper.selectList(new LambdaQueryWrapper<AlertNotifyLog>()
                .eq(eventId != null, AlertNotifyLog::getEventId, eventId)
                .orderByDesc(AlertNotifyLog::getCreatedAt))
                .stream()
                .map(this::toNotifyLogResponse)
                .collect(Collectors.toList());
    }

    public AlertNotifyLogResponse retryNotify(AlertNotifyRetryRequest req) {
        AlertNotifyLog notifyLog = notifyLogMapper.selectById(req.getNotifyLogId());
        if (notifyLog == null) {
            throw new BizException("通知记录不存在");
        }
        AlertChannel channel = channelMapper.selectById(notifyLog.getChannelId());
        notifyLog.setSendStatus("PENDING");
        notifyLog.setFailureReason(null);
        notifyLog.setProviderMsgId(null);
        notifyLog.setSentAt(null);
        notifyLogMapper.updateById(notifyLog);
        AlertNotifyLog after = notifyService.dispatch(notifyLog, channel);
        return toNotifyLogResponse(after);
    }

    /**
     * 手工生成测试告警，直接复用真实告警链路：写事件 → 归并 → 通知 → AI 摘要 → SSE 广播。
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertEventResponse createTestEvent(AlertEventTestRequest request) {
        AlertRule rule = ruleMapper.selectById(request.getRuleId());
        if (rule == null) {
            throw new BizException("告警规则不存在");
        }
        MonitorObject object = objectMapper.selectById(request.getObjectId());
        if (object == null) {
            throw new BizException("告警对象不存在");
        }
        // 校验绑定关系
        Long bound = ruleObjectRelMapper.selectCount(new LambdaQueryWrapper<AlertRuleObjectRel>()
                .eq(AlertRuleObjectRel::getRuleId, rule.getId())
                .eq(AlertRuleObjectRel::getObjectId, object.getId()));
        if (bound == null || bound == 0) {
            throw new BizException("该告警对象未绑定到所选规则");
        }
        return triggerEvent(rule, object, request.getCurrentValue(),
                StrUtil.blankToDefault(request.getEventReason(), "手工触发测试告警"));
    }

    /**
     * 真实告警入口：规则引擎调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertEventResponse triggerEvent(AlertRule rule, MonitorObject object,
                                           String currentValue, String reason) {
        AlertEvent event = new AlertEvent();
        event.setEventNo("ALERT-" + LocalDate.now().format(DATE_FMT) + "-" + shortId());
        event.setRuleId(rule.getId());
        event.setObjectId(object.getId());
        event.setObjectType(object.getObjectType());
        event.setObjectName(object.getObjectName());
        event.setMetricCode(rule.getStatus() == null ? "" : "");
        event.setMetricCode(StrUtil.blankToDefault(rule.getRuleCode(), "")); // 占位，下面会被覆盖
        // 取规则第一个条件作为代表性指标（事件层面），完整条件存于规则
        // 这里简化：metric 信息我们存 rule.ruleName + currentValue
        event.setMetricCode(StrUtil.blankToDefault(rule.getRuleCode(), ""));
        event.setMetricName(rule.getRuleName());
        event.setAlertLevel(rule.getAlertLevel());
        event.setEventStatus(Enums.EventStatus.PENDING);
        event.setCurrentValue(StrUtil.blankToDefault(currentValue, "异常"));
        event.setEventTitle(buildTitle(rule, object, currentValue));
        event.setEventContent(buildContent(rule, object, currentValue, reason));
        event.setEventReason(reason);
        event.setAiSummaryStatus("PENDING");
        LocalDateTime now = LocalDateTime.now();
        event.setFirstTriggeredAt(now);
        event.setLastTriggeredAt(now);
        eventMapper.insert(event);

        // 归并到 incident
        AlertIncident incident = incidentMerger.assign(event, object);
        event.setIncidentId(incident.getId());
        eventMapper.updateById(event);

        // 触发渠道通知
        List<AlertRuleChannelRel> channelRels = ruleChannelRelMapper.selectList(
                new LambdaQueryWrapper<AlertRuleChannelRel>().eq(AlertRuleChannelRel::getRuleId, rule.getId()));
        for (AlertRuleChannelRel rel : channelRels) {
            AlertChannel channel = channelMapper.selectById(rel.getChannelId());
            if (channel == null) continue;
            AlertNotifyLog notifyLog = new AlertNotifyLog();
            notifyLog.setEventId(event.getId());
            notifyLog.setRuleId(rule.getId());
            notifyLog.setChannelId(channel.getId());
            notifyLog.setChannelType(channel.getChannelType());
            notifyLog.setReceiverValue(rel.getReceiverValue());
            notifyLog.setNotifyTitle(event.getEventTitle());
            notifyLog.setNotifyContent(event.getEventContent());
            notifyService.dispatch(notifyLog, channel);
        }

        AlertEventResponse resp = toResponse(event, true);
        streamService.broadcast("event-created", resp);

        // 异步生成 AI 摘要
        try {
            summaryService.summarizeAsync(event.getId());
        } catch (Exception e) {
            log.warn("trigger summarize failed: {}", e.getMessage());
        }
        return resp;
    }

    // ---------------- helpers ----------------

    private String mapActionToStatus(String action, String before) {
        return switch (action) {
            case "CONFIRM" -> Enums.EventStatus.CONFIRMED;
            case "RECOVER" -> Enums.EventStatus.RECOVERED;
            case "CLOSE" -> Enums.EventStatus.CLOSED;
            default -> before; // COMMENT 不改状态
        };
    }

    private String buildTitle(AlertRule rule, MonitorObject object, String currentValue) {
        return "[%s] %s · %s 触发 · 当前值=%s".formatted(
                levelLabel(rule.getAlertLevel()),
                object.getObjectName(),
                rule.getRuleName(),
                StrUtil.blankToDefault(currentValue, "异常"));
    }

    private String buildContent(AlertRule rule, MonitorObject object,
                                String currentValue, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("规则：").append(rule.getRuleName()).append("\n");
        sb.append("对象：").append(object.getObjectName()).append("（").append(object.getObjectCode()).append("）\n");
        sb.append("级别：").append(levelLabel(rule.getAlertLevel())).append("\n");
        sb.append("当前值：").append(StrUtil.blankToDefault(currentValue, "异常")).append("\n");
        if (StrUtil.isNotBlank(rule.getDescription())) {
            sb.append("说明：").append(rule.getDescription()).append("\n");
        }
        if (StrUtil.isNotBlank(reason)) {
            sb.append("触发原因：").append(reason).append("\n");
        }
        return sb.toString();
    }

    private String levelLabel(String level) {
        if (level == null) return "";
        return switch (level) {
            case "NOTICE" -> "提示";
            case "NORMAL" -> "一般";
            case "SERIOUS" -> "严重";
            case "CRITICAL" -> "紧急";
            default -> level;
        };
    }

    private AlertEventResponse toResponse(AlertEvent e, boolean detail) {
        AlertEventResponse.AlertEventResponseBuilder b = AlertEventResponse.builder()
                .id(e.getId())
                .eventNo(e.getEventNo())
                .incidentId(e.getIncidentId())
                .ruleId(e.getRuleId())
                .objectId(e.getObjectId())
                .objectType(e.getObjectType())
                .objectName(e.getObjectName())
                .metricCode(e.getMetricCode())
                .metricName(e.getMetricName())
                .alertLevel(e.getAlertLevel())
                .eventStatus(e.getEventStatus())
                .currentValue(e.getCurrentValue())
                .thresholdValue(e.getThresholdValue())
                .eventTitle(e.getEventTitle())
                .eventContent(e.getEventContent())
                .eventReason(e.getEventReason())
                .aiSummary(e.getAiSummary())
                .aiSummaryStatus(e.getAiSummaryStatus())
                .aiReasoning(e.getAiReasoning())
                .firstTriggeredAt(e.getFirstTriggeredAt())
                .lastTriggeredAt(e.getLastTriggeredAt())
                .confirmedAt(e.getConfirmedAt())
                .recoveredAt(e.getRecoveredAt())
                .closedAt(e.getClosedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt());

        if (detail) {
            AlertRule rule = ruleMapper.selectById(e.getRuleId());
            if (rule != null) b.ruleName(rule.getRuleName());

            List<AlertEventHandleLog> logs = handleLogMapper.selectList(new LambdaQueryWrapper<AlertEventHandleLog>()
                    .eq(AlertEventHandleLog::getEventId, e.getId())
                    .orderByAsc(AlertEventHandleLog::getCreatedAt));
            b.handleLogs(logs.stream().map(this::toHandleLogResponse).collect(Collectors.toList()));

            List<AlertNotifyLog> notifies = notifyLogMapper.selectList(new LambdaQueryWrapper<AlertNotifyLog>()
                    .eq(AlertNotifyLog::getEventId, e.getId())
                    .orderByAsc(AlertNotifyLog::getCreatedAt));
            b.notifyLogs(notifies.stream().map(this::toNotifyLogResponse).collect(Collectors.toList()));
        }

        return b.build();
    }

    private AlertEventHandleLogResponse toHandleLogResponse(AlertEventHandleLog log) {
        return AlertEventHandleLogResponse.builder()
                .id(log.getId())
                .eventId(log.getEventId())
                .actionType(log.getActionType())
                .actionTypeName(actionLabel(log.getActionType()))
                .beforeStatus(log.getBeforeStatus())
                .afterStatus(log.getAfterStatus())
                .operatorName(log.getOperatorName())
                .operatorPhone(log.getOperatorPhone())
                .actionComment(log.getActionComment())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private AlertNotifyLogResponse toNotifyLogResponse(AlertNotifyLog log) {
        return AlertNotifyLogResponse.builder()
                .id(log.getId())
                .eventId(log.getEventId())
                .ruleId(log.getRuleId())
                .channelId(log.getChannelId())
                .channelType(log.getChannelType())
                .receiverValue(log.getReceiverValue())
                .notifyTitle(log.getNotifyTitle())
                .notifyContent(log.getNotifyContent())
                .sendStatus(log.getSendStatus())
                .providerMsgId(log.getProviderMsgId())
                .failureReason(log.getFailureReason())
                .sentAt(log.getSentAt())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String actionLabel(String action) {
        if (action == null) return "";
        return switch (action) {
            case "CONFIRM" -> "确认";
            case "RECOVER" -> "恢复";
            case "CLOSE" -> "关闭";
            case "COMMENT" -> "备注";
            default -> action;
        };
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
