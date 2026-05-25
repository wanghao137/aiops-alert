package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.AlertChannelRequest;
import com.aiops.alert.dto.AlertChannelResponse;
import com.aiops.alert.dto.AlertChannelStatsResponse;
import com.aiops.alert.dto.AlertChannelTestRequest;
import com.aiops.alert.dto.AlertNotifyLogResponse;
import com.aiops.alert.service.core.AlertChannelService;
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
@RequestMapping("/alert-channels")
public class AlertChannelController {

    private final AlertChannelService service;

    public AlertChannelController(AlertChannelService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<AlertChannelResponse>> list(
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.list(channelType, status, keyword));
    }

    @GetMapping("/{id}")
    public Result<AlertChannelResponse> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    public Result<AlertChannelResponse> save(@Valid @RequestBody AlertChannelRequest request) {
        return Result.success(service.save(request));
    }

    @PostMapping("/{id}/toggle")
    public Result<AlertChannelResponse> toggle(@PathVariable Long id) {
        return Result.success(service.toggle(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @PostMapping("/test")
    public Result<AlertNotifyLogResponse> test(@Valid @RequestBody AlertChannelTestRequest request) {
        return Result.success(service.test(request));
    }

    @GetMapping("/stats")
    public Result<AlertChannelStatsResponse> stats() {
        return Result.success(service.stats());
    }
}
