package com.aiops.alert.service.core;

import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.DashboardResponse;
import com.aiops.alert.entity.AlertChannel;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertIncident;
import com.aiops.alert.entity.AlertNotifyLog;
import com.aiops.alert.entity.AlertRule;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertChannelMapper;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertIncidentMapper;
import com.aiops.alert.mapper.AlertNotifyLogMapper;
import com.aiops.alert.mapper.AlertRuleMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final DateTimeFormatter SQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MonitorObjectMapper objectMapper;
    private final AlertRuleMapper ruleMapper;
    private final AlertChannelMapper channelMapper;
    private final AlertEventMapper eventMapper;
    private final AlertNotifyLogMapper notifyLogMapper;
    private final AlertIncidentMapper incidentMapper;
    private final AlertEventService eventService;

    public DashboardService(MonitorObjectMapper objectMapper,
                            AlertRuleMapper ruleMapper,
                            AlertChannelMapper channelMapper,
                            AlertEventMapper eventMapper,
                            AlertNotifyLogMapper notifyLogMapper,
                            AlertIncidentMapper incidentMapper,
                            AlertEventService eventService) {
        this.objectMapper = objectMapper;
        this.ruleMapper = ruleMapper;
        this.channelMapper = channelMapper;
        this.eventMapper = eventMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.incidentMapper = incidentMapper;
        this.eventService = eventService;
    }

    public DashboardResponse load() {
        long objectTotal = objectMapper.selectCount(null);
        long enabledObjectTotal = objectMapper.selectCount(new LambdaQueryWrapper<MonitorObject>()
                .eq(MonitorObject::getStatus, Enums.Status.ENABLED));
        long ruleTotal = ruleMapper.selectCount(null);
        long enabledRuleTotal = ruleMapper.selectCount(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getStatus, Enums.Status.ENABLED));
        long channelTotal = channelMapper.selectCount(null);
        long enabledChannelTotal = channelMapper.selectCount(new LambdaQueryWrapper<AlertChannel>()
                .eq(AlertChannel::getStatus, Enums.Status.ENABLED));

        long eventTotal = eventMapper.selectCount(null);
        long pendingEventTotal = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getEventStatus, Enums.EventStatus.PENDING));
        long seriousEventTotal = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getAlertLevel, Enums.AlertLevel.SERIOUS));
        long criticalEventTotal = eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getAlertLevel, Enums.AlertLevel.CRITICAL));
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long notifyFailedToday = notifyLogMapper.selectCount(new LambdaQueryWrapper<AlertNotifyLog>()
                .eq(AlertNotifyLog::getSendStatus, Enums.NotifyStatus.FAILED)
                .ge(AlertNotifyLog::getCreatedAt, startOfDay));
        long openIncidentTotal = incidentMapper.selectCount(new LambdaQueryWrapper<AlertIncident>()
                .eq(AlertIncident::getStatus, "OPEN"));

        // 分布统计（容错：如果没数据，返回字典 0 值）
        List<DashboardResponse.StatItem> statusDist = pickDistribution(
                eventMapper.countByStatus(),
                Map.of(
                        "PENDING", "待处理",
                        "CONFIRMED", "已确认",
                        "RECOVERED", "已恢复",
                        "CLOSED", "已关闭"
                ));
        List<DashboardResponse.StatItem> levelDist = pickDistribution(
                eventMapper.countByLevel(),
                Map.of(
                        "NOTICE", "提示",
                        "NORMAL", "一般",
                        "SERIOUS", "严重",
                        "CRITICAL", "紧急"
                ));
        List<DashboardResponse.StatItem> objectTypeDist = pickDistribution(
                eventMapper.countByObjectType(),
                Map.of(
                        "SERVER", "服务器",
                        "DATABASE", "数据库",
                        "SYNC_JOB", "数据同步作业",
                        "PROCESS_JOB", "数据加工作业"
                ));

        // 七日趋势：按 first_triggered_at 在 Java 端分组（避免方言差异）
        LocalDateTime weekAgo = LocalDate.now().minusDays(6).atStartOfDay();
        List<Map<String, Object>> trendRows = eventMapper.trendRowsSince(weekAgo.format(SQL_DATE));
        Map<String, long[]> trendIndex = new HashMap<>(); // [total, pending, recovered, critical]
        for (Map<String, Object> r : trendRows) {
            Map<String, Object> row = lowerKeys(r);
            String date = toLocalDate(row.get("first_triggered_at"));
            if (date == null) continue;
            long[] arr = trendIndex.computeIfAbsent(date, k -> new long[4]);
            arr[0]++;
            String st = String.valueOf(row.get("event_status"));
            String lv = String.valueOf(row.get("alert_level"));
            if ("PENDING".equals(st)) arr[1]++;
            if ("RECOVERED".equals(st)) arr[2]++;
            if ("CRITICAL".equals(lv)) arr[3]++;
        }
        List<DashboardResponse.TrendItem> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = LocalDate.now().minusDays(6 - i);
            String key = d.toString();
            long[] arr = trendIndex.getOrDefault(key, new long[4]);
            trend.add(DashboardResponse.TrendItem.builder()
                    .date(key)
                    .total(arr[0])
                    .pending(arr[1])
                    .recovered(arr[2])
                    .critical(arr[3])
                    .build());
        }

        // 规则命中 Top
        List<DashboardResponse.RuleHitItem> hitTop = new ArrayList<>();
        List<Map<String, Object>> hitRows = eventMapper.ruleHitTop(weekAgo.format(SQL_DATE));
        if (!hitRows.isEmpty()) {
            List<Map<String, Object>> normalized = hitRows.stream()
                    .map(this::lowerKeys).toList();
            List<Long> ruleIds = normalized.stream().map(r -> toLong(r, "ruleid")).toList();
            Map<Long, AlertRule> ruleIndex = new HashMap<>();
            for (AlertRule r : ruleMapper.selectBatchIds(ruleIds)) {
                ruleIndex.put(r.getId(), r);
            }
            for (Map<String, Object> r : normalized) {
                Long rid = toLong(r, "ruleid");
                AlertRule rule = ruleIndex.get(rid);
                hitTop.add(DashboardResponse.RuleHitItem.builder()
                        .ruleId(rid)
                        .ruleName(rule == null ? "已删除规则#" + rid : rule.getRuleName())
                        .objectType(rule == null ? "" : rule.getObjectType())
                        .hitCount(toLong(r, "hitcount"))
                        .build());
            }
        }

        // 最近 8 条事件
        var recent = eventService.list(null, null, null, null, 8);

        return DashboardResponse.builder()
                .objectTotal(objectTotal).enabledObjectTotal(enabledObjectTotal)
                .ruleTotal(ruleTotal).enabledRuleTotal(enabledRuleTotal)
                .channelTotal(channelTotal).enabledChannelTotal(enabledChannelTotal)
                .eventTotal(eventTotal).pendingEventTotal(pendingEventTotal)
                .seriousEventTotal(seriousEventTotal).criticalEventTotal(criticalEventTotal)
                .notifyFailedToday(notifyFailedToday)
                .openIncidentTotal(openIncidentTotal)
                .statusDistribution(statusDist)
                .levelDistribution(levelDist)
                .objectTypeDistribution(objectTypeDist)
                .sevenDayTrend(trend)
                .ruleHitTop(hitTop)
                .recentEvents(recent)
                .build();
    }

    private List<DashboardResponse.StatItem> pickDistribution(List<Map<String, Object>> rows,
                                                              Map<String, String> dict) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = lowerKeys(r);
            map.put(String.valueOf(row.get("code")), toLong(row, "total"));
        }
        List<DashboardResponse.StatItem> list = new ArrayList<>();
        for (Map.Entry<String, String> e : dict.entrySet()) {
            list.add(DashboardResponse.StatItem.builder()
                    .code(e.getKey())
                    .name(e.getValue())
                    .value(map.getOrDefault(e.getKey(), 0L))
                    .build());
        }
        return list;
    }

    private long toLong(Map<String, Object> row, String key) {
        if (row == null) return 0;
        Object v = row.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 把任意时间字段（Timestamp/LocalDateTime/String）转成 yyyy-MM-dd。 */
    private String toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate().toString();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toLocalDate().toString();
        }
        if (value instanceof java.time.LocalDate ld) {
            return ld.toString();
        }
        String s = value.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /** 行的 key 在 H2 里可能是大写，统一转小写。 */
    private Map<String, Object> lowerKeys(Map<String, Object> row) {
        if (row == null) return Map.of();
        Map<String, Object> out = new HashMap<>(row.size());
        for (Map.Entry<String, Object> e : row.entrySet()) {
            out.put(e.getKey() == null ? null : e.getKey().toLowerCase(), e.getValue());
        }
        return out;
    }
}
