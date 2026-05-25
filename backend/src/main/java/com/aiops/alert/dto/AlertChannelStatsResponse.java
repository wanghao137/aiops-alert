package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertChannelStatsResponse {

    private long total;
    private long enabled;
    private long sentToday;
    private long failedToday;

    private List<TypeStat> byType;

    @Data
    @Builder
    public static class TypeStat {
        private String channelType;
        private String channelTypeName;
        private long total;
        private long enabled;
    }
}
