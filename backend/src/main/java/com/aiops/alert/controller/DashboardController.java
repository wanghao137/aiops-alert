package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.DashboardResponse;
import com.aiops.alert.service.core.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public Result<DashboardResponse> dashboard() {
        return Result.success(service.load());
    }
}
