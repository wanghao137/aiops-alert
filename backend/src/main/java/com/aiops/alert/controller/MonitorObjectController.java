package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.MonitorObjectRequest;
import com.aiops.alert.dto.MonitorObjectResponse;
import com.aiops.alert.dto.MonitorObjectStatsResponse;
import com.aiops.alert.service.core.MonitorObjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控对象 REST 接口。
 */
@RestController
@RequestMapping("/monitor-objects")
public class MonitorObjectController {

    private final MonitorObjectService service;

    public MonitorObjectController(MonitorObjectService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<MonitorObjectResponse>> list(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(service.list(objectType, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<MonitorObjectResponse> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    public Result<MonitorObjectResponse> save(@Valid @RequestBody MonitorObjectRequest request) {
        return Result.success(service.save(request));
    }

    @PostMapping("/{id}/toggle")
    public Result<MonitorObjectResponse> toggle(@PathVariable Long id) {
        return Result.success(service.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<MonitorObjectStatsResponse> stats() {
        return Result.success(service.stats());
    }
}
