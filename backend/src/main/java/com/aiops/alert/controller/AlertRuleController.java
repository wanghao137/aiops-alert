package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.AlertRuleRequest;
import com.aiops.alert.dto.AlertRuleResponse;
import com.aiops.alert.dto.AlertRuleStatsResponse;
import com.aiops.alert.service.core.AlertRuleService;
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

@RestController
@RequestMapping("/alert-rules")
public class AlertRuleController {

    private final AlertRuleService service;

    public AlertRuleController(AlertRuleService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<AlertRuleResponse>> list(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.list(objectType, alertLevel, status, keyword));
    }

    @GetMapping("/{id}")
    public Result<AlertRuleResponse> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    public Result<AlertRuleResponse> save(@Valid @RequestBody AlertRuleRequest request) {
        return Result.success(service.save(request));
    }

    @PostMapping("/{id}/toggle")
    public Result<AlertRuleResponse> toggle(@PathVariable Long id) {
        return Result.success(service.toggle(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<AlertRuleStatsResponse> stats() {
        return Result.success(service.stats());
    }
}
