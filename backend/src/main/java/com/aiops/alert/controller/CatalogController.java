package com.aiops.alert.controller;

import com.aiops.alert.common.Enums;
import com.aiops.alert.common.MetricCatalog;
import com.aiops.alert.common.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典接口：指标 / 比较符 / 对象类型 / 告警级别 / 渠道类型。
 */
@RestController
@RequestMapping("/catalog")
public class CatalogController {

    @GetMapping("/object-types")
    public Result<List<Map<String, String>>> objectTypes() {
        return Result.success(List.of(
                Map.of("code", "SERVER", "name", "服务器"),
                Map.of("code", "DATABASE", "name", "数据库"),
                Map.of("code", "SYNC_JOB", "name", "数据同步作业"),
                Map.of("code", "PROCESS_JOB", "name", "数据加工作业")
        ));
    }

    @GetMapping("/channel-types")
    public Result<List<Map<String, String>>> channelTypes() {
        return Result.success(List.of(
                Map.of("code", "WECOM", "name", "企业微信"),
                Map.of("code", "EMAIL", "name", "邮件"),
                Map.of("code", "SMS", "name", "短信")
        ));
    }

    @GetMapping("/alert-levels")
    public Result<List<Map<String, String>>> alertLevels() {
        return Result.success(List.of(
                Map.of("code", "NOTICE", "name", "提示"),
                Map.of("code", "NORMAL", "name", "一般"),
                Map.of("code", "SERIOUS", "name", "严重"),
                Map.of("code", "CRITICAL", "name", "紧急")
        ));
    }

    @GetMapping("/event-statuses")
    public Result<List<Map<String, String>>> eventStatuses() {
        return Result.success(List.of(
                Map.of("code", "PENDING", "name", "待处理"),
                Map.of("code", "CONFIRMED", "name", "已确认"),
                Map.of("code", "RECOVERED", "name", "已恢复"),
                Map.of("code", "CLOSED", "name", "已关闭")
        ));
    }

    @GetMapping("/compare-ops")
    public Result<List<MetricCatalog.CompareOp>> compareOps() {
        return Result.success(MetricCatalog.compareOps());
    }

    @GetMapping("/metrics")
    public Result<Map<String, List<MetricCatalog.Metric>>> metrics(
            @RequestParam(required = false) String objectType) {
        if (objectType != null && !objectType.isEmpty()) {
            Map<String, List<MetricCatalog.Metric>> map = new LinkedHashMap<>();
            map.put(objectType, MetricCatalog.forObjectType(objectType));
            return Result.success(map);
        }
        Map<String, List<MetricCatalog.Metric>> all = new LinkedHashMap<>();
        for (String t : Enums.ObjectType.ALL) {
            all.put(t, MetricCatalog.forObjectType(t));
        }
        return Result.success(all);
    }
}
