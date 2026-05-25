package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.MetricCatalogResponse;
import com.aiops.alert.service.core.MetricCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标字典 / 比较符 / 对象类型 / 告警级别 字典接口。
 * 前端首屏拉一次即可完成动态表单。
 */
@RestController
@RequestMapping("/metric-catalog")
public class MetricCatalogController {

    private final MetricCatalogService service;

    public MetricCatalogController(MetricCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public Result<MetricCatalogResponse> all() {
        return Result.success(MetricCatalogResponse.builder()
                .metricsByType(service.all())
                .compareOps(service.compareOps())
                .objectTypes(List.of(
                        new MetricCatalogResponse.TypeOption("SERVER", "服务器"),
                        new MetricCatalogResponse.TypeOption("DATABASE", "数据库"),
                        new MetricCatalogResponse.TypeOption("SYNC_JOB", "数据同步作业"),
                        new MetricCatalogResponse.TypeOption("PROCESS_JOB", "数据加工作业")))
                .alertLevels(List.of(
                        new MetricCatalogResponse.LevelOption("NOTICE", "提示", "sky"),
                        new MetricCatalogResponse.LevelOption("NORMAL", "一般", "blue"),
                        new MetricCatalogResponse.LevelOption("SERIOUS", "严重", "amber"),
                        new MetricCatalogResponse.LevelOption("CRITICAL", "紧急", "red")))
                .build());
    }
}
