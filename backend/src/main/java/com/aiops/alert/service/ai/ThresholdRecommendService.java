package com.aiops.alert.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.MetricCatalog;
import com.aiops.alert.entity.MetricSample;
import com.aiops.alert.mapper.MetricSampleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 阈值智能推荐：基于历史指标分位数。
 */
@Slf4j
@Service
public class ThresholdRecommendService {

    private final MetricSampleMapper metricSampleMapper;

    public ThresholdRecommendService(MetricSampleMapper metricSampleMapper) {
        this.metricSampleMapper = metricSampleMapper;
    }

    /**
     * 返回结构：
     * {
     *   "metricCode":"cpu_usage",
     *   "samples": 1024,
     *   "p50": 60.0, "p95": 78.0, "p99": 90.0, "max": 95.0,
     *   "recommendations": [
     *     { "label":"高敏感", "value":"75", "explain":"接近 P95"},
     *     { "label":"中等敏感", "value":"85", "explain":"P99 + 安全余量"},
     *     { "label":"低敏感", "value":"92", "explain":"接近近 7 天最大值"}
     *   ]
     * }
     */
    public Map<String, Object> recommend(Long objectId, String objectType, String metricCode) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("metricCode", metricCode);

        if (StrUtil.isBlank(metricCode)) {
            resp.put("recommendations", List.of());
            resp.put("samples", 0);
            return resp;
        }

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<MetricSample> wrapper = new LambdaQueryWrapper<MetricSample>()
                .eq(MetricSample::getMetricCode, metricCode)
                .ge(MetricSample::getSampledAt, weekAgo)
                .orderByAsc(MetricSample::getNumericValue);
        if (objectId != null) {
            wrapper.eq(MetricSample::getObjectId, objectId);
        }
        List<MetricSample> samples = metricSampleMapper.selectList(wrapper);
        List<Double> values = new ArrayList<>();
        for (MetricSample s : samples) {
            if (s.getNumericValue() != null) {
                values.add(s.getNumericValue().doubleValue());
            }
        }
        Collections.sort(values);

        resp.put("samples", values.size());

        // 元数据
        MetricCatalog.Metric meta = StrUtil.isBlank(objectType) ? null
                : MetricCatalog.findMetric(objectType, metricCode);
        if (meta != null) {
            resp.put("metricName", meta.getName());
            resp.put("unit", meta.getUnit());
        }

        if (values.isEmpty()) {
            // 回退到经验阈值
            resp.put("recommendations", fallbackRecommendations(metricCode, meta));
            resp.put("source", "EMPIRICAL");
            return resp;
        }

        double p50 = percentile(values, 0.5);
        double p95 = percentile(values, 0.95);
        double p99 = percentile(values, 0.99);
        double max = values.get(values.size() - 1);
        resp.put("p50", round(p50));
        resp.put("p95", round(p95));
        resp.put("p99", round(p99));
        resp.put("max", round(max));

        List<Map<String, Object>> recos = new ArrayList<>();
        recos.add(reco("高敏感", round(Math.max(p95 - 2, p50)), "接近 P95，会更早触发"));
        recos.add(reco("中等敏感", round(Math.min(p99, max - 2)), "P99 + 安全余量，平衡告警与噪声"));
        recos.add(reco("低敏感", round(max), "接近近 7 天最大值，仅在异常时触发"));
        resp.put("recommendations", recos);
        resp.put("source", "HISTORY");
        return resp;
    }

    private List<Map<String, Object>> fallbackRecommendations(String metricCode, MetricCatalog.Metric meta) {
        // 简单经验值
        Map<String, double[]> EMPIRICAL = Map.of(
                "cpu_usage", new double[]{75, 85, 92},
                "memory_usage", new double[]{80, 88, 95},
                "disk_usage", new double[]{70, 85, 95},
                "tablespace_usage", new double[]{75, 85, 95},
                "active_connections", new double[]{100, 200, 400},
                "slow_query_per_min", new double[]{20, 50, 100},
                "replication_lag", new double[]{60, 180, 300},
                "job_duration", new double[]{20, 30, 60}
        );
        double[] vals = EMPIRICAL.getOrDefault(metricCode, new double[]{50, 80, 95});
        List<Map<String, Object>> recos = new ArrayList<>();
        recos.add(reco("高敏感", vals[0], "经验值，敏感档位"));
        recos.add(reco("中等敏感", vals[1], "经验值，常用档位"));
        recos.add(reco("低敏感", vals[2], "经验值，仅极端情况触发"));
        return recos;
    }

    private Map<String, Object> reco(String label, double value, String explain) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("value", round(value));
        m.put("explain", explain);
        return m;
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    private double round(double d) {
        return BigDecimal.valueOf(d).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
