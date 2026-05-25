package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonitorObjectStatsResponse {

    private long total;
    private long enabled;
    private List<TypeStat> byType;

    @Data
    @Builder
    public static class TypeStat {
        private String objectType;
        private String objectTypeName;
        private long total;
        private long enabled;
    }
}
