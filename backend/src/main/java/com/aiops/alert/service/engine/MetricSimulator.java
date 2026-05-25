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
 * 指标模拟器：定时为每个启用的监控对象生成 mock 指标，写入 metric_sample。
 *
 * 设计：
 *  - 每个对象 + 指标维护一个"游走中心"，让数据像真实时序一样有惯性
 *  - 偶尔注入 spike，便于规则触发演示
 */
@Slf4j
@Service
public class MetricSimulator {

    private final MonitorObjectMapper objectMapper;
    private final MetricSampleMapper sampleMapper;
    private final Random random = new Random();

    /** key: objectId|metricCode  -> 当前游走中心 */
    private final Map<String, Double> drift = new ConcurrentHashMap<>();

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
                    String value = nextEnum(m);
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
        log.debug("MetricSimulator tick: wrote {} samples for {} objects", samples.size(), objects.size());
    }

    private double nextNumeric(Long objectId, MetricCatalog.Metric m) {
        String key = objectId + "|" + m.getCode();
        double center = drift.computeIfAbsent(key, k -> baseLine(m));
        double sigma = baseSigma(m);
        // 95% 概率小幅游走，5% 概率一次 spike
        double v;
        if (random.nextDouble() < 0.05) {
            v = center + sigma * (random.nextBoolean() ? 4 : -4);
        } else {
            v = center + (random.nextGaussian() * sigma);
            // 让 center 缓慢漂移
            center = clamp(center + random.nextGaussian() * sigma * 0.1, lower(m), upper(m));
            drift.put(key, center);
        }
        v = clamp(v, lower(m), upper(m));
        return Math.round(v * 100.0) / 100.0;
    }

    private String nextEnum(MetricCatalog.Metric m) {
        // 95% 第一项（健康），5% 末位（不健康）
        if (m.getEnumValues() == null || m.getEnumValues().isEmpty()) return "OK";
        if (random.nextDouble() < 0.05) {
            return m.getEnumValues().get(m.getEnumValues().size() - 1);
        }
        return m.getEnumValues().get(0);
    }

    private double baseLine(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_usage", "tablespace_usage", "disk_io_busy" -> 55;
            case "active_connections" -> 80;
            case "slow_query_per_min" -> 8;
            case "replication_lag" -> 10;
            case "lock_waiting" -> 2;
            case "qps" -> 1500;
            case "job_duration" -> 5;
            case "schedule_lag" -> 1;
            case "sync_rows", "output_rows" -> 1200;
            case "consecutive_fail" -> 0;
            case "network_in", "network_out" -> 200;
            default -> 50;
        };
    }

    private double baseSigma(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_io_busy" -> 8;
            case "disk_usage", "tablespace_usage" -> 1.5;
            case "active_connections" -> 25;
            case "slow_query_per_min" -> 4;
            case "replication_lag" -> 6;
            case "qps" -> 200;
            case "job_duration" -> 2;
            case "sync_rows", "output_rows" -> 200;
            case "network_in", "network_out" -> 60;
            default -> 5;
        };
    }

    private double lower(MetricCatalog.Metric m) {
        return switch (m.getCode()) {
            case "cpu_usage", "memory_usage", "disk_usage",
                 "tablespace_usage", "disk_io_busy" -> 0;
            default -> 0;
        };
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
}
