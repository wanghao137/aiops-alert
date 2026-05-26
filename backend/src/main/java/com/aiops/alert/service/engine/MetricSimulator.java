package com.aiops.alert.service.engine;

import com.aiops.alert.common.Enums;
import com.aiops.alert.common.MetricCatalog;
import com.aiops.alert.entity.MetricSample;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.MetricSampleMapper;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 指标模拟器（故事模式）：让指标像真实时序一样有"故事"。
 *
 * 状态机：HEALTHY → WARNING(爬升) → ANOMALY(超阈值) → RECOVERING(回落) → HEALTHY
 *
 * 默认 HEALTHY 在基线附近小幅游走；
 * 每个 tick 有小概率（约 3-5 分钟一次）选中某对象某指标进入异常爬升；
 * ANOMALY 状态会持续 8-15 个 tick，足以命中规则的"连续 N 次"；
 * 然后慢慢恢复。
 *
 * 也支持手动触发：演示时点击 forceStory 立刻让某对象某指标进入异常。
 */
@Slf4j
@Service
public class MetricSimulator {

    private static final double STORY_TRIGGER_PROBABILITY = 0.04; // 单次 tick 触发故事的概率

    private final MonitorObjectMapper objectMapper;
    private final MetricSampleMapper sampleMapper;
    private final Random random = new Random();

    /** key: objectId|metricCode → 当前指标状态 */
    private final Map<String, MetricState> states = new ConcurrentHashMap<>();

    @Value("${aiops.simulator.enabled:true}")
    private boolean enabled;

    public MetricSimulator(MonitorObjectMapper objectMapper, MetricSampleMapper sampleMapper) {
        this.objectMapper = objectMapper;
        this.sampleMapper = sampleMapper;
    }

    @Scheduled(fixedDelayString = "${aiops.simulator.interval-seconds:15}000")
    public void tick() {
        if (!enabled) return;
        List<MonitorObject> objects = objectMapper.selectList(new LambdaQueryWrapper<MonitorObject>()
                .eq(MonitorObject::getStatus, Enums.Status.ENABLED));
        if (objects.isEmpty()) return;

        // 每个 tick 有概率把一个健康对象的某个指标推入"故事"
        maybeStartStory(objects);

        List<MetricSample> samples = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (MonitorObject object : objects) {
            List<MetricCatalog.Metric> metrics = MetricCatalog.forObjectType(object.getObjectType());
            for (MetricCatalog.Metric m : metrics) {
                MetricSample s = new MetricSample();
                s.setObjectId(object.getId());
                s.setMetricCode(m.getCode());
                s.setSampledAt(now);
                if ("NUMERIC".equals(m.getValueType())) {
                    double value = nextNumeric(object.getId(), m);
                    s.setNumericValue(BigDecimal.valueOf(value));
                    s.setMetricValue(String.valueOf(value));
                } else if ("ENUM".equals(m.getValueType())) {
                    String value = nextEnum(object.getId(), m);
                    s.setMetricValue(value);
                } else {
                    s.setMetricValue("OK");
                }
                samples.add(s);
            }
        }
        for (MetricSample s : samples) {
            sampleMapper.insert(s);
        }
        log.debug("MetricSimulator tick: wrote {} samples for {} objects (active stories: {})",
                samples.size(), objects.size(), countAnomalies());
    }

    /** 演示用：手动让某对象某指标立即进入异常状态。 */
    public void forceStory(Long objectId, String metricCode) {
        MetricCatalog.Metric metric = findMetric(objectId, metricCode);
        if (metric == null) return;
        String key = stateKey(objectId, metricCode);
        MetricState st = states.computeIfAbsent(key, k -> new MetricState(metric));
        st.startAnomaly(metric, random);
        log.info("forceStory: object={} metric={} → ANOMALY for {} ticks",
                objectId, metricCode, st.remaining);
    }

    /** 给定对象 + 指标找元数据 */
    private MetricCatalog.Metric findMetric(Long objectId, String metricCode) {
        MonitorObject obj = objectMapper.selectById(objectId);
        if (obj == null) return null;
        return MetricCatalog.forObjectType(obj.getObjectType()).stream()
                .filter(m -> m.getCode().equals(metricCode))
                .findFirst().orElse(null);
    }

    private void maybeStartStory(List<MonitorObject> objects) {
        // 已经有 ≥2 个故事进行中就先不开新故事，避免演示时太乱
        if (countAnomalies() >= 2) return;
        if (random.nextDouble() > STORY_TRIGGER_PROBABILITY) return;

        MonitorObject target = objects.get(random.nextInt(objects.size()));
        List<MetricCatalog.Metric> metrics = MetricCatalog.forObjectType(target.getObjectType()).stream()
                // 优先挑能被规则匹配的指标（数值 + 枚举都行）
                .filter(m -> "NUMERIC".equals(m.getValueType()) || "ENUM".equals(m.getValueType()))
                .toList();
        if (metrics.isEmpty()) return;
        MetricCatalog.Metric metric = metrics.get(random.nextInt(metrics.size()));

        String key = stateKey(target.getId(), metric.getCode());
        MetricState st = states.computeIfAbsent(key, k -> new MetricState(metric));
        if (st.phase != Phase.HEALTHY) return;

        st.startAnomaly(metric, random);
        log.info("Story started: object={} metric={} → {} ticks anomaly",
                target.getObjectName(), metric.getName(), st.remaining);
    }

    private long countAnomalies() {
        return states.values().stream()
                .filter(s -> s.phase == Phase.WARNING || s.phase == Phase.ANOMALY)
                .count();
    }

    // ------------------ 数值 ------------------

    private double nextNumeric(Long objectId, MetricCatalog.Metric m) {
        String key = stateKey(objectId, m.getCode());
        MetricState st = states.computeIfAbsent(key, k -> new MetricState(m));

        double base = baseLine(m);
        double sigma = baseSigma(m);

        // 状态推进
        double targetValue;
        switch (st.phase) {
            case WARNING -> {
                // 朝异常目标爬升
                st.current = lerp(st.current, st.targetValue, 0.35) + random.nextGaussian() * sigma * 0.2;
                if (--st.remaining <= 0) {
                    st.phase = Phase.ANOMALY;
                    st.remaining = 6 + random.nextInt(6); // 6-11 次保持高位
                }
                targetValue = st.current;
            }
            case ANOMALY -> {
                // 在异常区高位震荡
                targetValue = st.targetValue + random.nextGaussian() * sigma * 0.3;
                if (--st.remaining <= 0) {
                    st.phase = Phase.RECOVERING;
                    st.remaining = 4 + random.nextInt(4); // 4-7 次缓慢回落
                }
                st.current = targetValue;
            }
            case RECOVERING -> {
                st.current = lerp(st.current, base, 0.4) + random.nextGaussian() * sigma * 0.2;
                if (--st.remaining <= 0) {
                    st.phase = Phase.HEALTHY;
                    st.current = base;
                }
                targetValue = st.current;
            }
            default -> {
                // HEALTHY：基线 + 小噪声
                targetValue = base + random.nextGaussian() * sigma;
                st.current = targetValue;
            }
        }

        targetValue = clamp(targetValue, lower(m), upper(m));
        return Math.round(targetValue * 100.0) / 100.0;
    }

    // ------------------ 枚举 ------------------

    private String nextEnum(Long objectId, MetricCatalog.Metric m) {
        if (m.getEnumValues() == null || m.getEnumValues().isEmpty()) return "OK";
        String healthy = m.getEnumValues().get(0);
        String unhealthy = m.getEnumValues().get(m.getEnumValues().size() - 1);

        String key = stateKey(objectId, m.getCode());
        MetricState st = states.computeIfAbsent(key, k -> new MetricState(m));
        return switch (st.phase) {
            case WARNING, ANOMALY -> {
                if (--st.remaining <= 0) {
                    if (st.phase == Phase.WARNING) {
                        st.phase = Phase.ANOMALY;
                        st.remaining = 4 + random.nextInt(4);
                    } else {
                        st.phase = Phase.RECOVERING;
                        st.remaining = 2 + random.nextInt(3);
                    }
                }
                yield unhealthy;
            }
            case RECOVERING -> {
                if (--st.remaining <= 0) {
                    st.phase = Phase.HEALTHY;
                }
                yield healthy;
            }
            default -> healthy;
        };
    }

    // ------------------ 基线 / 阈值近似值 ------------------

    private double baseLine(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_io_busy", "tablespace_usage" -> 50;
            case "disk_usage" -> 60;
            case "active_connections" -> 80;
            case "slow_query_per_min" -> 6;
            case "replication_lag" -> 10;
            case "lock_waiting" -> 1;
            case "qps" -> 1500;
            case "job_duration" -> 5;
            case "schedule_lag" -> 1;
            case "sync_rows", "output_rows" -> 1200;
            case "consecutive_fail" -> 0;
            case "network_in", "network_out" -> 200;
            default -> 30;
        };
    }

    private double baseSigma(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_io_busy" -> 5;
            case "disk_usage", "tablespace_usage" -> 1.0;
            case "active_connections" -> 10;
            case "slow_query_per_min" -> 2;
            case "replication_lag" -> 4;
            case "qps" -> 150;
            case "job_duration" -> 1.5;
            case "sync_rows", "output_rows" -> 200;
            case "network_in", "network_out" -> 50;
            default -> 3;
        };
    }

    /** 异常时的目标值（明显超过常见阈值） */
    private double anomalyTarget(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_io_busy" -> 92;
            case "disk_usage", "tablespace_usage" -> 90;
            case "active_connections" -> 280;
            case "slow_query_per_min" -> 130;
            case "replication_lag" -> 480;
            case "lock_waiting" -> 25;
            case "qps" -> 8500;
            case "job_duration" -> 45;
            case "schedule_lag" -> 18;
            case "sync_rows" -> 0;
            case "output_rows" -> 0;
            case "consecutive_fail" -> 5;
            case "network_in", "network_out" -> 950;
            default -> 95;
        };
    }

    private double lower(MetricCatalog.Metric m) {
        return 0;
    }

    private double upper(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_usage",
                 "tablespace_usage", "disk_io_busy" -> 100;
            default -> 100000;
        };
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private String stateKey(Long objectId, String metricCode) {
        return objectId + "|" + metricCode;
    }

    // ------------------ 内部状态 ------------------

    private enum Phase { HEALTHY, WARNING, ANOMALY, RECOVERING }

    private final class MetricState {
        Phase phase = Phase.HEALTHY;
        double current;
        double targetValue;
        int remaining;

        MetricState(MetricCatalog.Metric m) {
            this.current = baseLine(m);
        }

        /** 进入"故事"：先 WARNING 爬升，再 ANOMALY 高位 */
        void startAnomaly(MetricCatalog.Metric m, Random random) {
            phase = Phase.WARNING;
            remaining = 3 + random.nextInt(3); // 3-5 次爬升
            targetValue = anomalyTarget(m);
        }
    }
}
