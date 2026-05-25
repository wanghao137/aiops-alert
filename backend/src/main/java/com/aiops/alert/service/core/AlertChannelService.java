package com.aiops.alert.service.core;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.AlertChannelRequest;
import com.aiops.alert.dto.AlertChannelResponse;
import com.aiops.alert.dto.AlertChannelStatsResponse;
import com.aiops.alert.dto.AlertChannelTestRequest;
import com.aiops.alert.dto.AlertNotifyLogResponse;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertNotifyLog;
import com.aiops.alert.mapper.AlertChannelMapper;
import com.aiops.alert.mapper.AlertNotifyLogMapper;
import com.aiops.alert.service.notify.AlertNotifyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 告警渠道服务。
 */
@Service
public class AlertChannelService {

    private static final List<String> ALLOWED_TYPES = List.of(
            Enums.ChannelType.WECOM, Enums.ChannelType.EMAIL, Enums.ChannelType.SMS);

    private final AlertChannelMapper channelMapper;
    private final AlertNotifyLogMapper notifyLogMapper;
    private final AlertNotifyService notifyService;
    private final ObjectMapper jsonMapper;

    public AlertChannelService(AlertChannelMapper channelMapper,
                               AlertNotifyLogMapper notifyLogMapper,
                               AlertNotifyService notifyService,
                               ObjectMapper jsonMapper) {
        this.channelMapper = channelMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.notifyService = notifyService;
        this.jsonMapper = jsonMapper;
    }

    public List<AlertChannelResponse> list(String channelType, String status, String keyword) {
        LambdaQueryWrapper<AlertChannel> wrapper = new LambdaQueryWrapper<AlertChannel>()
                .eq(StrUtil.isNotBlank(channelType), AlertChannel::getChannelType, channelType)
                .eq(StrUtil.isNotBlank(status), AlertChannel::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(AlertChannel::getChannelName, keyword)
                        .or().like(AlertChannel::getChannelCode, keyword)
                        .or().like(AlertChannel::getProviderName, keyword))
                .orderByAsc(AlertChannel::getPriority)
                .orderByDesc(AlertChannel::getUpdatedAt);

        List<AlertChannel> entities = channelMapper.selectList(wrapper);
        List<Long> ids = entities.stream().map(AlertChannel::getId).collect(Collectors.toList());
        Map<Long, AlertNotifyLog> lastLogMap = lastLogByChannel(ids);

        return entities.stream()
                .map(c -> toResponse(c, lastLogMap.get(c.getId())))
                .collect(Collectors.toList());
    }

    public AlertChannelResponse get(Long id) {
        AlertChannel entity = channelMapper.selectById(id);
        if (entity == null) {
            throw new BizException("渠道不存在");
        }
        AlertNotifyLog last = lastLogOf(id);
        return toResponse(entity, last);
    }

    public AlertChannelResponse save(AlertChannelRequest request) {
        if (!ALLOWED_TYPES.contains(request.getChannelType())) {
            throw new BizException("渠道类型不合法：" + request.getChannelType());
        }
        validateConfigJson(request.getChannelType(), request.getConfigJson());

        AlertChannel entity;
        if (request.getId() == null) {
            entity = new AlertChannel();
        } else {
            entity = channelMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException("渠道不存在");
            }
        }

        String code = StrUtil.isNotBlank(request.getChannelCode())
                ? request.getChannelCode().trim()
                : "CH-" + shortId();
        if (entity.getId() == null || !code.equals(entity.getChannelCode())) {
            Long count = channelMapper.selectCount(new LambdaQueryWrapper<AlertChannel>()
                    .eq(AlertChannel::getChannelCode, code)
                    .ne(entity.getId() != null, AlertChannel::getId, entity.getId()));
            if (count != null && count > 0) {
                throw new BizException("渠道编码已存在：" + code);
            }
        }

        entity.setChannelCode(code);
        entity.setChannelName(request.getChannelName().trim());
        entity.setChannelType(request.getChannelType());
        entity.setProviderName(request.getProviderName());
        entity.setStatus(StrUtil.blankToDefault(request.getStatus(), Enums.Status.ENABLED));
        entity.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        entity.setConfigJson(request.getConfigJson());
        entity.setDescription(request.getDescription());

        if (entity.getId() == null) {
            channelMapper.insert(entity);
        } else {
            channelMapper.updateById(entity);
        }
        return toResponse(entity, lastLogOf(entity.getId()));
    }

    public AlertChannelResponse toggle(Long id) {
        AlertChannel entity = channelMapper.selectById(id);
        if (entity == null) {
            throw new BizException("渠道不存在");
        }
        entity.setStatus(Enums.Status.ENABLED.equals(entity.getStatus())
                ? Enums.Status.DISABLED : Enums.Status.ENABLED);
        channelMapper.updateById(entity);
        return toResponse(entity, lastLogOf(id));
    }

    public void delete(Long id) {
        AlertChannel entity = channelMapper.selectById(id);
        if (entity == null) {
            return;
        }
        channelMapper.deleteById(id);
    }

    public AlertNotifyLogResponse test(AlertChannelTestRequest request) {
        AlertChannel channel = channelMapper.selectById(request.getChannelId());
        if (channel == null) {
            throw new BizException("渠道不存在");
        }

        AlertNotifyLog log = new AlertNotifyLog();
        log.setEventId(0L);
        log.setRuleId(0L);
        log.setChannelId(channel.getId());
        log.setChannelType(channel.getChannelType());
        log.setReceiverValue(StrUtil.blankToDefault(request.getReceiverValue(),
                defaultReceiver(channel)));
        log.setNotifyTitle(StrUtil.blankToDefault(request.getTitle(), "AIOps Alert · 渠道测试"));
        log.setNotifyContent(StrUtil.blankToDefault(request.getContent(),
                "这是一条来自 AIOps Alert 智能监控告警系统的渠道测试通知，收到说明渠道配置正确。"));

        AlertNotifyLog after = notifyService.dispatch(log, channel);
        return toLogResponse(after);
    }

    public AlertChannelStatsResponse stats() {
        long total = channelMapper.selectCount(null);
        long enabled = channelMapper.selectCount(new LambdaQueryWrapper<AlertChannel>()
                .eq(AlertChannel::getStatus, Enums.Status.ENABLED));

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long sentToday = notifyLogMapper.selectCount(new LambdaQueryWrapper<AlertNotifyLog>()
                .eq(AlertNotifyLog::getSendStatus, Enums.NotifyStatus.SUCCESS)
                .ge(AlertNotifyLog::getCreatedAt, startOfDay));
        long failedToday = notifyLogMapper.selectCount(new LambdaQueryWrapper<AlertNotifyLog>()
                .eq(AlertNotifyLog::getSendStatus, Enums.NotifyStatus.FAILED)
                .ge(AlertNotifyLog::getCreatedAt, startOfDay));

        List<Map<String, Object>> rows = channelMapper.countByType();
        List<AlertChannelStatsResponse.TypeStat> byType = new ArrayList<>();
        for (String type : ALLOWED_TYPES) {
            long t = 0;
            long e = 0;
            for (Map<String, Object> row : rows) {
                if (type.equals(row.get("channelType"))) {
                    t = toLong(row.get("total"));
                    e = toLong(row.get("enabled"));
                    break;
                }
            }
            byType.add(AlertChannelStatsResponse.TypeStat.builder()
                    .channelType(type)
                    .channelTypeName(typeName(type))
                    .total(t)
                    .enabled(e)
                    .build());
        }

        return AlertChannelStatsResponse.builder()
                .total(total)
                .enabled(enabled)
                .sentToday(sentToday)
                .failedToday(failedToday)
                .byType(byType)
                .build();
    }

    // ---------------- helpers ----------------

    private void validateConfigJson(String channelType, String configJson) {
        if (StrUtil.isBlank(configJson)) {
            return;
        }
        try {
            jsonMapper.readTree(configJson);
        } catch (Exception e) {
            throw new BizException("配置不是合法 JSON：" + e.getMessage());
        }
    }

    private String defaultReceiver(AlertChannel channel) {
        if (StrUtil.isBlank(channel.getConfigJson())) {
            return null;
        }
        try {
            var node = jsonMapper.readTree(channel.getConfigJson());
            if (Enums.ChannelType.EMAIL.equals(channel.getChannelType())) {
                return node.path("defaultReceivers").asText("");
            }
            if (Enums.ChannelType.SMS.equals(channel.getChannelType())) {
                return node.path("defaultReceivers").asText("");
            }
            // WECOM 不需要 receiver, 在 markdown @ 提到的电话里
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private AlertChannelResponse toResponse(AlertChannel entity, AlertNotifyLog last) {
        return AlertChannelResponse.builder()
                .id(entity.getId())
                .channelCode(entity.getChannelCode())
                .channelName(entity.getChannelName())
                .channelType(entity.getChannelType())
                .channelTypeName(typeName(entity.getChannelType()))
                .providerName(entity.getProviderName())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .configJson(entity.getConfigJson())
                .description(entity.getDescription())
                .lastSendStatus(last == null ? null : last.getSendStatus())
                .lastFailureReason(last == null ? null : last.getFailureReason())
                .lastSentAt(last == null ? null : last.getSentAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AlertNotifyLogResponse toLogResponse(AlertNotifyLog log) {
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

    private Map<Long, AlertNotifyLog> lastLogByChannel(List<Long> channelIds) {
        Map<Long, AlertNotifyLog> map = new HashMap<>();
        if (channelIds == null || channelIds.isEmpty()) {
            return map;
        }
        for (Long id : channelIds) {
            AlertNotifyLog last = lastLogOf(id);
            if (last != null) {
                map.put(id, last);
            }
        }
        return map;
    }

    private AlertNotifyLog lastLogOf(Long channelId) {
        if (channelId == null) {
            return null;
        }
        List<AlertNotifyLog> rows = notifyLogMapper.selectList(new LambdaQueryWrapper<AlertNotifyLog>()
                .eq(AlertNotifyLog::getChannelId, channelId)
                .orderByDesc(AlertNotifyLog::getCreatedAt)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String typeName(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "WECOM" -> "企业微信";
            case "EMAIL" -> "邮件";
            case "SMS" -> "短信";
            default -> type;
        };
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
