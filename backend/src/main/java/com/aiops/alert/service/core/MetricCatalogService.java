package com.aiops.alert.service.core;

import com.aiops.alert.common.Enums;
import com.aiops.alert.common.MetricCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * 指标字典外观服务（对前端 / NL 提供的对外形态）。
 *
 * 内部数据来自 {@link MetricCatalog} 静态字典 — 由规则引擎、模拟器使用 ——
 * 在这里包装成前端 / AI 友好的形状（valueType=numeric/state, defaultCompareOp 等），
 * 保持两端字典一致。
 */
@Service
public class MetricCatalogService {

    private final Map<String, List<Metric>> metricsByType;
    private final List<CompareOp> compareOps;

    public MetricCatalogService() {
        this.metricsByType = buildMetrics();
        this.compareOps = buildCompareOps();
    }

    /** 全部指标，按对象类型分组返回。 */
    public Map<String, List<Metric>> all() {
        return metricsByType;
    }

    /** 某种对象类型下的指标。 */
    public List<Metric> metricsOfType(String objectType) {
        if (objectType == null) {
            return Collections.emptyList();
        }
        return metricsByType.getOrDefault(objectType, Collections.emptyList());
    }

    /** 比较符字典。 */
    public List<CompareOp> compareOps() {
        return compareOps;
    }

    /** 给定对象类型 + 指标编码，找指标元数据。 */
    public Metric findMetric(String objectType, String metricCode) {
        for (Metric m : metricsOfType(objectType)) {
            if (m.getCode().equals(metricCode)) {
                return m;
            }
        }
        return null;
    }

    // ---------------- 数据：从 common.MetricCatalog 转换 ----------------

    private Map<String, List<Metric>> buildMetrics() {
        Map<String, List<Metric>> map = new LinkedHashMap<>();
        for (String type : Enums.ObjectType.ALL) {
            List<Metric> arr = new ArrayList<>();
            for (MetricCatalog.Metric src : MetricCatalog.forObjectType(type)) {
                arr.add(toMetric(src));
            }
            map.put(type, arr);
        }
        return map;
    }

    private Metric toMetric(MetricCatalog.Metric src) {
        boolean isEnum = "ENUM".equalsIgnoreCase(src.getValueType());
        String valueType = isEnum ? "state" : "numeric";
        List<EnumOption> options = new ArrayList<>();
        if (isEnum && src.getEnumValues() != null) {
            for (String v : src.getEnumValues()) {
                options.add(new EnumOption(v, enumLabel(v)));
            }
        }
        // 默认比较符：取声明里的第一个
        String defaultOp = (src.getCompareOps() != null && !src.getCompareOps().isEmpty())
                ? src.getCompareOps().get(0)
                : (isEnum ? "EQ" : "GT");
        // 默认阈值：数值给经验值；ENUM 给最后一个枚举（往往是失败态）
        String defaultThreshold = isEnum
                ? (src.getEnumValues() == null || src.getEnumValues().isEmpty()
                    ? null : src.getEnumValues().get(src.getEnumValues().size() - 1))
                : defaultThresholdOf(src.getCode());

        return Metric.builder()
                .code(src.getCode())
                .name(src.getName())
                .valueType(valueType)
                .unit(src.getUnit())
                .min(rangeMin(src))
                .max(rangeMax(src))
                .defaultCompareOp(defaultOp)
                .defaultThreshold(defaultThreshold)
                .options(options)
                .build();
    }

    private Integer rangeMin(MetricCatalog.Metric m) {
        if (m.getRange() == null) return null;
        try {
            String[] parts = m.getRange().split("~");
            return parts.length == 2 ? Integer.parseInt(parts[0].trim()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer rangeMax(MetricCatalog.Metric m) {
        if (m.getRange() == null) return null;
        try {
            String[] parts = m.getRange().split("~");
            return parts.length == 2 ? Integer.parseInt(parts[1].trim()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultThresholdOf(String code) {
        return switch (code) {
            case "cpu_usage" -> "85";
            case "memory_usage" -> "90";
            case "disk_usage" -> "85";
            case "disk_io_busy" -> "60";
            case "active_connections" -> "200";
            case "slow_query_per_min" -> "50";
            case "replication_lag" -> "300";
            case "lock_waiting" -> "10";
            case "tablespace_usage" -> "85";
            case "qps" -> "5000";
            case "job_duration" -> "30";
            case "schedule_lag" -> "10";
            case "sync_rows", "output_rows" -> "1";
            case "consecutive_fail" -> "3";
            case "network_in", "network_out" -> "800";
            default -> "";
        };
    }

    private String enumLabel(String value) {
        return switch (value) {
            case "ONLINE" -> "在线";
            case "OFFLINE" -> "离线";
            case "RUNNING" -> "运行中";
            case "DOWN" -> "异常";
            case "OPEN" -> "可达";
            case "CLOSED" -> "不可达";
            case "OK" -> "正常";
            case "FAIL" -> "无法连接";
            case "SUCCESS" -> "成功";
            case "FAILED" -> "失败";
            case "EMPTY" -> "空跑";
            case "NORMAL" -> "正常";
            case "ON_TIME" -> "按时";
            case "TIMEOUT" -> "超时";
            default -> value;
        };
    }

    private List<CompareOp> buildCompareOps() {
        return Arrays.asList(
                new CompareOp("GT", "大于", ">", "numeric"),
                new CompareOp("GE", "大于等于", ">=", "numeric"),
                new CompareOp("LT", "小于", "<", "numeric"),
                new CompareOp("LE", "小于等于", "<=", "numeric"),
                new CompareOp("EQ", "等于", "=", "any"),
                new CompareOp("NE", "不等于", "!=", "any"),
                new CompareOp("OFFLINE", "处于离线", "is OFFLINE", "state"),
                new CompareOp("FAILED", "处于失败", "is FAILED", "state"),
                new CompareOp("TIMEOUT", "处于超时", "is TIMEOUT", "state"),
                new CompareOp("IN", "属于集合", "in (...)", "any")
        );
    }

    // ---------------- 模型 ----------------

    @Data
    @Builder
    public static class Metric {
        private String code;
        private String name;
        /** numeric / state */
        private String valueType;
        private String unit;
        private Integer min;
        private Integer max;
        private String defaultCompareOp;
        private String defaultThreshold;
        @Builder.Default
        private List<EnumOption> options = new ArrayList<>();
    }

    @Data
    public static class CompareOp {
        private final String code;
        private final String label;
        private final String symbol;
        /** numeric / state / any */
        private final String inputKind;
    }

    @Data
    public static class EnumOption {
        private final String value;
        private final String label;
    }
}
