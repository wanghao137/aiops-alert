package com.aiops.alert.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 监控指标 + 比较符字典。
 *
 * 指标按"对象类型"分组，前端构建动态规则表单、规则引擎评估、AI NL2Rule 上下文都用同一份。
 */
public final class MetricCatalog {

    private MetricCatalog() {}

    @Data
    @Builder
    @AllArgsConstructor
    public static class Metric {
        /** 指标编码（系统内部唯一） */
        private final String code;
        /** 指标显示名 */
        private final String name;
        /** 单位：%/ms/秒/MB/次 等，nullable */
        private final String unit;
        /** 数据类型：NUMERIC / ENUM / BOOL */
        private final String valueType;
        /** 推荐的比较符（编码） */
        private final List<String> compareOps;
        /** ENUM 时的可选值 */
        private final List<String> enumValues;
        /** 推荐的告警级别 */
        private final String suggestLevel;
        /** 数值范围说明，例如 "0~100" */
        private final String range;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class CompareOp {
        private final String code;
        private final String label;
        private final String symbol;
        private final boolean needThreshold;
    }

    private static final List<CompareOp> COMPARE_OPS = List.of(
            CompareOp.builder().code("GT").label("大于").symbol(">").needThreshold(true).build(),
            CompareOp.builder().code("GE").label("大于等于").symbol(">=").needThreshold(true).build(),
            CompareOp.builder().code("LT").label("小于").symbol("<").needThreshold(true).build(),
            CompareOp.builder().code("LE").label("小于等于").symbol("<=").needThreshold(true).build(),
            CompareOp.builder().code("EQ").label("等于").symbol("=").needThreshold(true).build(),
            CompareOp.builder().code("NE").label("不等于").symbol("!=").needThreshold(true).build(),
            CompareOp.builder().code("OFFLINE").label("离线").symbol("offline").needThreshold(false).build(),
            CompareOp.builder().code("FAILED").label("失败").symbol("failed").needThreshold(false).build(),
            CompareOp.builder().code("TIMEOUT").label("超时").symbol("timeout").needThreshold(false).build()
    );

    public static List<CompareOp> compareOps() {
        return COMPARE_OPS;
    }

    public static String compareOpLabel(String code) {
        return COMPARE_OPS.stream()
                .filter(o -> o.getCode().equals(code))
                .findFirst()
                .map(CompareOp::getSymbol)
                .orElse(code);
    }

    /**
     * 各对象类型支持的指标。
     */
    private static final Map<String, List<Metric>> METRICS = Map.of(
            Enums.ObjectType.SERVER, List.of(
                    metric("cpu_usage", "CPU 使用率", "%", "NUMERIC", List.of("GT", "GE", "LT"),
                            null, "SERIOUS", "0~100"),
                    metric("memory_usage", "内存使用率", "%", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", "0~100"),
                    metric("disk_usage", "磁盘使用率", "%", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", "0~100"),
                    metric("disk_io_busy", "磁盘 IO 繁忙度", "%", "NUMERIC", List.of("GT"),
                            null, "NORMAL", "0~100"),
                    metric("network_in", "入流量", "Mbps", "NUMERIC", List.of("GT"),
                            null, "NORMAL", null),
                    metric("network_out", "出流量", "Mbps", "NUMERIC", List.of("GT"),
                            null, "NORMAL", null),
                    metric("host_status", "主机在线状态", null, "ENUM", List.of("EQ", "OFFLINE"),
                            List.of("ONLINE", "OFFLINE"), "CRITICAL", null),
                    metric("process_alive", "关键进程存活", null, "ENUM", List.of("EQ", "FAILED"),
                            List.of("RUNNING", "DOWN"), "CRITICAL", null),
                    metric("port_alive", "端口可达", null, "ENUM", List.of("EQ", "FAILED"),
                            List.of("OPEN", "CLOSED"), "CRITICAL", null)
            ),
            Enums.ObjectType.DATABASE, List.of(
                    metric("db_connectable", "数据库可连接", null, "ENUM", List.of("EQ", "FAILED"),
                            List.of("OK", "FAIL"), "CRITICAL", null),
                    metric("active_connections", "活跃连接数", "个", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", null),
                    metric("slow_query_per_min", "慢查询/分钟", "次", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", null),
                    metric("replication_lag", "主从延迟", "秒", "NUMERIC", List.of("GT", "GE"),
                            null, "CRITICAL", null),
                    metric("lock_waiting", "锁等待数", "个", "NUMERIC", List.of("GT"),
                            null, "SERIOUS", null),
                    metric("tablespace_usage", "表空间使用率", "%", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", "0~100"),
                    metric("qps", "QPS", "次/秒", "NUMERIC", List.of("GT", "LT"),
                            null, "NORMAL", null)
            ),
            Enums.ObjectType.SYNC_JOB, List.of(
                    metric("job_status", "作业执行状态", null, "ENUM", List.of("EQ", "FAILED"),
                            List.of("SUCCESS", "RUNNING", "FAILED"), "SERIOUS", null),
                    metric("job_duration", "作业耗时", "分钟", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", null),
                    metric("schedule_lag", "调度延迟", "分钟", "NUMERIC", List.of("GT"),
                            null, "NORMAL", null),
                    metric("sync_rows", "同步条数", "条", "NUMERIC", List.of("LT", "EQ"),
                            null, "NORMAL", null),
                    metric("consecutive_fail", "连续失败次数", "次", "NUMERIC", List.of("GE"),
                            null, "CRITICAL", null)
            ),
            Enums.ObjectType.PROCESS_JOB, List.of(
                    metric("job_status", "作业执行状态", null, "ENUM", List.of("EQ", "FAILED"),
                            List.of("SUCCESS", "RUNNING", "FAILED"), "SERIOUS", null),
                    metric("job_duration", "执行耗时", "分钟", "NUMERIC", List.of("GT", "GE"),
                            null, "SERIOUS", null),
                    metric("schedule_timeout", "调度超时", null, "ENUM", List.of("TIMEOUT", "EQ"),
                            List.of("ON_TIME", "TIMEOUT"), "SERIOUS", null),
                    metric("output_rows", "输出量", "条", "NUMERIC", List.of("LT", "EQ"),
                            null, "NORMAL", null),
                    metric("output_zero", "空跑", null, "ENUM", List.of("EQ"),
                            List.of("NORMAL", "EMPTY"), "SERIOUS", null),
                    metric("consecutive_fail", "连续失败次数", "次", "NUMERIC", List.of("GE"),
                            null, "CRITICAL", null)
            )
    );

    public static Map<String, List<Metric>> all() {
        return METRICS;
    }

    public static List<Metric> forObjectType(String objectType) {
        return METRICS.getOrDefault(objectType, Collections.emptyList());
    }

    public static List<Metric> allMetricsFlatten() {
        List<Metric> all = new ArrayList<>();
        METRICS.values().forEach(all::addAll);
        return all;
    }

    public static Metric findMetric(String objectType, String metricCode) {
        return forObjectType(objectType).stream()
                .filter(m -> m.getCode().equals(metricCode))
                .findFirst()
                .orElse(null);
    }

    private static Metric metric(String code, String name, String unit, String valueType,
                                 List<String> ops, List<String> enums, String level, String range) {
        return Metric.builder()
                .code(code).name(name).unit(unit).valueType(valueType)
                .compareOps(ops)
                .enumValues(enums)
                .suggestLevel(level)
                .range(range)
                .build();
    }
}
