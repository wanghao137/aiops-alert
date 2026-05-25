package com.aiops.alert.dto;

import com.aiops.alert.service.core.MetricCatalogService;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricCatalogResponse {

    private Map<String, List<MetricCatalogService.Metric>> metricsByType;
    private List<MetricCatalogService.CompareOp> compareOps;
    private List<TypeOption> objectTypes;
    private List<LevelOption> alertLevels;

    @Data
    public static class TypeOption {
        private final String value;
        private final String label;
    }

    @Data
    public static class LevelOption {
        private final String value;
        private final String label;
        /** UI 颜色提示：blue / amber / red / sky */
        private final String tone;
    }
}
