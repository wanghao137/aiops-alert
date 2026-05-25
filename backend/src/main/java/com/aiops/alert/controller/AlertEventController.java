package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.AlertEventActionRequest;
import com.aiops.alert.dto.AlertEventResponse;
import com.aiops.alert.dto.AlertEventTestRequest;
import com.aiops.alert.dto.AlertNotifyLogResponse;
import com.aiops.alert.dto.AlertNotifyRetryRequest;
import com.aiops.alert.service.ai.EventSummaryService;
import com.aiops.alert.service.core.AlertEventService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alert-events")
public class AlertEventController {

    private final AlertEventService eventService;
    private final EventSummaryService summaryService;

    public AlertEventController(AlertEventService eventService,
                                EventSummaryService summaryService) {
        this.eventService = eventService;
        this.summaryService = summaryService;
    }

    @GetMapping
    public Result<List<AlertEventResponse>> list(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String eventStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return Result.success(eventService.list(objectType, alertLevel, eventStatus, keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<AlertEventResponse> get(@PathVariable Long id) {
        return Result.success(eventService.get(id));
    }

    @PostMapping("/action")
    public Result<AlertEventResponse> handle(@Valid @RequestBody AlertEventActionRequest request) {
        return Result.success(eventService.handle(request));
    }

    @PostMapping("/test")
    public Result<AlertEventResponse> test(@Valid @RequestBody AlertEventTestRequest request) {
        return Result.success(eventService.createTestEvent(request));
    }

    @PostMapping("/{id}/summarize")
    public Result<Void> summarize(@PathVariable Long id) {
        summaryService.summarizeAsync(id);
        return Result.success();
    }

    @GetMapping("/{id}/notify-logs")
    public Result<List<AlertNotifyLogResponse>> notifyLogs(@PathVariable Long id) {
        return Result.success(eventService.notifyLogs(id));
    }

    @PostMapping("/notify-logs/retry")
    public Result<AlertNotifyLogResponse> retry(@Valid @RequestBody AlertNotifyRetryRequest request) {
        return Result.success(eventService.retryNotify(request));
    }
}
