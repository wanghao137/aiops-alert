package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.DailyBriefResponse;
import com.aiops.alert.service.ai.DailyBriefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日态势简报接口。
 *
 * GET /daily-brief        取当前缓存的简报（首次调用会即时生成）
 * POST /daily-brief/refresh 强制刷新（会调 LLM）
 */
@RestController
@RequestMapping("/daily-brief")
public class DailyBriefController {

    private final DailyBriefService service;

    public DailyBriefController(DailyBriefService service) {
        this.service = service;
    }

    @GetMapping
    public Result<DailyBriefResponse> current() {
        return Result.success(service.current());
    }

    @PostMapping("/refresh")
    public Result<DailyBriefResponse> refresh() {
        return Result.success(service.refresh());
    }
}
