package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.AiCallLogQuery;
import com.aiops.alert.dto.AiCallLogResponse;
import com.aiops.alert.dto.AiStatsOverviewResponse;
import com.aiops.alert.dto.PageResult;
import com.aiops.alert.service.ai.AiCallStatsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用统计 REST 接口。
 *
 * 路径前缀 /ai-stats，挂在已有 /api 全局前缀下。
 * 所有接口走全局 @ControllerAdvice 包成 Result 后返回。
 */
@RestController
@RequestMapping("/ai-stats")
public class AiCallStatsController {

    private final AiCallStatsService service;

    public AiCallStatsController(AiCallStatsService service) {
        this.service = service;
    }

    /** Hero + 场景分布 + 7 日趋势 + 成本卡 一次拉完。 */
    @GetMapping("/overview")
    public Result<AiStatsOverviewResponse> overview() {
        return Result.success(service.loadOverview());
    }

    /** 慢调用 Top N，默认近 7 天 Top 10。 */
    @GetMapping("/slow")
    public Result<List<AiCallLogResponse>> slow(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(service.slowTop(days, limit));
    }

    /** 流水分页，按 scene / modelName / status 过滤；不返回大字段 payload。 */
    @GetMapping("/logs")
    public Result<PageResult<AiCallLogResponse>> logs(@Valid @ModelAttribute AiCallLogQuery query) {
        return Result.success(service.page(query));
    }

    /** 单条详情，含完整 requestPayload / responsePayload / reasoningContent。 */
    @GetMapping("/logs/{id}")
    public Result<AiCallLogResponse> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }
}
