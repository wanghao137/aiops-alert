package com.aiops.alert.service.ai;

import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.DailyBriefResponse;
import com.aiops.alert.dto.DailyBriefResponse.HighlightEvent;
import com.aiops.alert.dto.DailyBriefResponse.Snapshot;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertIncident;
import com.aiops.alert.entity.AlertNotifyLog;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertIncidentMapper;
import com.aiops.alert.mapper.AlertNotifyLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每日态势简报服务。
 *
 * - 每天 8:00 定时刷新（cron）
 * - 启动 60 秒后预热一次（避免空状态）
 * - 暴露手动 refresh() 给 controller 调用
 * - 缓存最近一份 brief，单例内存，重启清空
 */
@Slf4j
@Service
public class DailyBriefService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AlertEventMapper eventMapper;
    private final AlertIncidentMapper incidentMapper;
    private final AlertNotifyLogMapper notifyLogMapper;
    private final LlmClient llmClient;
    private final LlmModelConfigService llmConfigService;

    private volatile DailyBriefResponse cached;

    public DailyBriefService(AlertEventMapper eventMapper,
                             AlertIncidentMapper incidentMapper,
                             AlertNotifyLogMapper notifyLogMapper,
                             LlmClient llmClient,
                             LlmModelConfigService llmConfigService) {
        this.eventMapper = eventMapper;
        this.incidentMapper = incidentMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.llmClient = llmClient;
        this.llmConfigService = llmConfigService;
    }

    /** 启动后预热：让 dashboard 一进来就有简报可读。 */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void warmup() {
        try {
            log.info("DailyBrief warmup start");
            refresh();
            log.info("DailyBrief warmup ok: status={}", cached == null ? "null" : cached.getStatus());
        } catch (Exception e) {
            log.warn("DailyBrief warmup failed: {}", e.getMessage());
        }
    }

    /** 每天 8:00 自动刷新简报。 */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scheduledRefresh() {
        log.info("DailyBrief scheduled refresh at 8:00");
        refresh();
    }

    /** 手动刷新（controller 暴露）。 */
    public DailyBriefResponse refresh() {
        DailyBriefResponse next = build();
        cached = next;
        return next;
    }

    /** 取缓存；如果还没有，立刻 build 一份。 */
    public DailyBriefResponse current() {
        if (cached == null) {
            return refresh();
        }
        return cached;
    }

    // ---------------- 私有：构造简报 ----------------

    private DailyBriefResponse build() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBefore = today.minusDays(2);

        // 昨日 + 前日 事件
        List<AlertEvent> yesterdayEvents = eventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getFirstTriggeredAt, yesterday.atStartOfDay())
                .lt(AlertEvent::getFirstTriggeredAt, today.atStartOfDay()));
        List<AlertEvent> dayBeforeEvents = eventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getFirstTriggeredAt, dayBefore.atStartOfDay())
                .lt(AlertEvent::getFirstTriggeredAt, yesterday.atStartOfDay()));

        long totalEvents = yesterdayEvents.size();
        long criticalEvents = yesterdayEvents.stream()
                .filter(e -> Enums.AlertLevel.CRITICAL.equals(e.getAlertLevel()))
                .count();
        long pendingEvents = yesterdayEvents.stream()
                .filter(e -> Enums.EventStatus.PENDING.equals(e.getEventStatus()))
                .count();
        long recoveredEvents = yesterdayEvents.stream()
                .filter(e -> Enums.EventStatus.RECOVERED.equals(e.getEventStatus())
                        || Enums.EventStatus.CLOSED.equals(e.getEventStatus()))
                .count();

        long openIncidents = incidentMapper.selectCount(new LambdaQueryWrapper<AlertIncident>()
                .eq(AlertIncident::getStatus, "OPEN"));

        long notifyFailed = notifyLogMapper.selectCount(new LambdaQueryWrapper<AlertNotifyLog>()
                .eq(AlertNotifyLog::getSendStatus, Enums.NotifyStatus.FAILED)
                .ge(AlertNotifyLog::getCreatedAt, yesterday.atStartOfDay())
                .lt(AlertNotifyLog::getCreatedAt, today.atStartOfDay()));

        double dayOverDay = 0.0;
        if (dayBeforeEvents.size() > 0) {
            dayOverDay = round1((totalEvents - dayBeforeEvents.size()) * 100.0 / dayBeforeEvents.size());
        } else if (totalEvents > 0) {
            dayOverDay = 100.0;
        }

        // 重点告警 Top 3：critical 优先 + 昨日时间倒序
        List<HighlightEvent> highlights = yesterdayEvents.stream()
                .sorted(Comparator.<AlertEvent, Integer>comparing(e ->
                                Enums.AlertLevel.CRITICAL.equals(e.getAlertLevel()) ? 0
                                        : Enums.AlertLevel.SERIOUS.equals(e.getAlertLevel()) ? 1 : 2)
                        .thenComparing(AlertEvent::getFirstTriggeredAt, Comparator.reverseOrder()))
                .limit(3)
                .map(this::toHighlight)
                .toList();

        Snapshot snapshot = Snapshot.builder()
                .totalEvents(totalEvents)
                .criticalEvents(criticalEvents)
                .pendingEvents(pendingEvents)
                .recoveredEvents(recoveredEvents)
                .openIncidents(openIncidents)
                .notifyFailed(notifyFailed)
                .dayOverDay(dayOverDay)
                .build();

        // 调 LLM 生成中文叙述
        String narrative = null;
        String status = "FALLBACK";
        try {
            if (llmConfigService.hasUsableConfig()) {
                narrative = generateNarrative(yesterday, snapshot, highlights);
                status = "SUCCESS";
            } else {
                narrative = fallbackNarrative(yesterday, snapshot);
            }
        } catch (Exception e) {
            log.warn("DailyBrief LLM generate failed: {}", e.getMessage());
            narrative = fallbackNarrative(yesterday, snapshot);
            status = "FAILED";
        }

        return DailyBriefResponse.builder()
                .generatedAt(LocalDateTime.now())
                .coverageDate(yesterday.format(DATE_FMT))
                .narrative(narrative)
                .status(status)
                .snapshot(snapshot)
                .highlights(highlights)
                .build();
    }

    private HighlightEvent toHighlight(AlertEvent e) {
        return HighlightEvent.builder()
                .id(e.getId())
                .eventNo(e.getEventNo())
                .objectName(e.getObjectName())
                .alertLevel(e.getAlertLevel())
                .eventTitle(e.getEventTitle())
                .eventStatus(e.getEventStatus())
                .triggeredAt(e.getFirstTriggeredAt() == null ? null : e.getFirstTriggeredAt().format(DATETIME_FMT))
                .build();
    }

    private String generateNarrative(LocalDate yesterday, Snapshot snap, List<HighlightEvent> highlights) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("日期：").append(yesterday.format(DATE_FMT)).append("\n");
        ctx.append("总事件数：").append(snap.getTotalEvents()).append("\n");
        ctx.append("紧急事件：").append(snap.getCriticalEvents()).append("\n");
        ctx.append("待处理：").append(snap.getPendingEvents()).append("\n");
        ctx.append("已恢复：").append(snap.getRecoveredEvents()).append("\n");
        ctx.append("活跃 Incident：").append(snap.getOpenIncidents()).append("\n");
        ctx.append("通知失败：").append(snap.getNotifyFailed()).append("\n");
        ctx.append("环比：").append(snap.getDayOverDay()).append("%\n");
        if (!highlights.isEmpty()) {
            ctx.append("重点告警：\n");
            for (HighlightEvent h : highlights) {
                ctx.append("- [").append(h.getAlertLevel()).append("] ")
                   .append(h.getObjectName()).append(" / ")
                   .append(h.getEventTitle()).append(" (")
                   .append(h.getEventStatus()).append(")\n");
            }
        }

        String systemPrompt = "你是一名运维 SRE 团队的态势分析师。基于昨日告警数据，写一段 80 ~ 150 字的中文简报。"
                + "要求：(1) 第一句给出整体定调（平稳/承压/严峻）；"
                + "(2) 突出最值得关注的对象或场景；"
                + "(3) 给一条清晰可执行的关注建议；"
                + "(4) 不要再列数字，让读者一眼读完；"
                + "(5) 输出纯文本不要 markdown。";

        LlmClient.ChatResult result = llmClient.chat("DAILY_BRIEF", systemPrompt, ctx.toString());
        String content = result.getContent();
        if (content == null) return fallbackNarrative(yesterday, snap);
        // 截到 200 字以内
        content = content.trim();
        if (content.length() > 220) content = content.substring(0, 200) + "…";
        return content;
    }

    /** 无 LLM 时的模板兜底，保证简报始终有内容。 */
    private String fallbackNarrative(LocalDate yesterday, Snapshot snap) {
        String tone;
        if (snap.getCriticalEvents() > 0) {
            tone = "运维态势承压";
        } else if (snap.getTotalEvents() > 0) {
            tone = "运维态势平稳，存在常规告警";
        } else {
            tone = "运维态势平稳，未触发任何告警";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(yesterday.format(DATE_FMT)).append(" ").append(tone).append("。");
        if (snap.getTotalEvents() > 0) {
            sb.append("共 ").append(snap.getTotalEvents()).append(" 条告警");
            if (snap.getCriticalEvents() > 0) {
                sb.append("，其中紧急 ").append(snap.getCriticalEvents()).append(" 条");
            }
            sb.append("，已恢复 ").append(snap.getRecoveredEvents()).append(" 条");
            sb.append("，仍有 ").append(snap.getPendingEvents()).append(" 条待处理");
            sb.append("。");
        }
        if (snap.getOpenIncidents() > 0) {
            sb.append("当前活跃 Incident ").append(snap.getOpenIncidents()).append(" 个，建议优先处理。");
        }
        if (snap.getNotifyFailed() > 0) {
            sb.append("有 ").append(snap.getNotifyFailed()).append(" 条通知发送失败，请检查渠道配置。");
        }
        return sb.toString();
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
