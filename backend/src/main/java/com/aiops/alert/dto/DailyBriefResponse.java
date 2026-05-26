package com.aiops.alert.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 每日态势简报。AI 生成的中文叙述 + 关键数字快照。
 *
 * 由 DailyBriefService 在每天 8:00 定时刷新，也可手动触发。
 * 单例内存缓存，重启清空。
 */
@Data
@Builder
public class DailyBriefResponse {

    /** 简报生成时间 */
    private LocalDateTime generatedAt;

    /** 覆盖的业务日期：yyyy-MM-dd（一般是昨日） */
    private String coverageDate;

    /** AI 生成的中文叙述（≤ 200 字），如果 LLM 不可用则为 null */
    private String narrative;

    /** 简报状态：SUCCESS（AI 生成）/ FALLBACK（无 LLM 时模板兜底）/ FAILED */
    private String status;

    /** 关键数字快照 */
    private Snapshot snapshot;

    /** 重点告警 Top 3（昨日 critical 或最长持续） */
    private List<HighlightEvent> highlights;

    @Data
    @Builder
    public static class Snapshot {
        private long totalEvents;
        private long criticalEvents;
        private long pendingEvents;
        private long recoveredEvents;
        private long openIncidents;
        private long notifyFailed;
        /** 昨日 vs 前日 总事件数环比（±%，0 = 持平） */
        private double dayOverDay;
    }

    @Data
    @Builder
    public static class HighlightEvent {
        private Long id;
        private String eventNo;
        private String objectName;
        private String alertLevel;
        private String eventTitle;
        private String eventStatus;
        private String triggeredAt;
    }
}
