package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.MonitorObjectDraftRequest;
import com.aiops.alert.dto.MonitorObjectDraftResponse;
import com.aiops.alert.service.ai.MonitorObjectDraftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/objects")
public class AiMonitorObjectController {

    private final MonitorObjectDraftService service;

    public AiMonitorObjectController(MonitorObjectDraftService service) {
        this.service = service;
    }

    @GetMapping("/availability")
    public Result<Boolean> availability() {
        return Result.success(service.available());
    }

    @PostMapping("/draft")
    public Result<MonitorObjectDraftResponse> draft(@Valid @RequestBody MonitorObjectDraftRequest request) {
        return Result.success(service.draft(request));
    }
}
