package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {

    private long objectTotal;
    private long enabledObjectTotal;
    private long ruleTotal;
    private long enabledRuleTotal;
    private long channelTotal;
    private long enabledChannelTotal;

    private long eventTotal;
    private long pendingEventTotal;
    private long seriousEventTotal;
    private long criticalEventTotal;
    private long notifyFailedToday;
    private long openIncidentTotal;

    private List<StatItem> statusDistribution;
    private List<StatItem> levelDistribution;
    private List<StatItem> objectTypeDistribution;
    private List<TrendItem> sevenDayTrend;
    private List<RuleHitItem> ruleHitTop;
    private List<AlertEventResponse> recentEvents;

    @Data
    @Builder
    public static class StatItem {
        private String code;
        private String name;
        private long value;
    }

    @Data
    @Builder
    public static class TrendItem {
        private String date;
        private long total;
        private long pending;
        private long recovered;
        private long critical;
    }

    @Data
    @Builder
    public static class RuleHitItem {
        private Long ruleId;
        private String ruleName;
        private String objectType;
        private long hitCount;
    }
}
