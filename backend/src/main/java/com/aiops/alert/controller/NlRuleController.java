package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.NlRuleDraftRequest;
import com.aiops.alert.dto.NlRuleDraftResponse;
import com.aiops.alert.service.ai.NlRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/rules")
public class NlRuleController {

    private final NlRuleService service;

    public NlRuleController(NlRuleService service) {
        this.service = service;
    }

    @GetMapping("/availability")
    public Result<Boolean> availability() {
        return Result.success(service.available());
    }

    @PostMapping("/draft")
    public Result<NlRuleDraftResponse> draft(@Valid @RequestBody NlRuleDraftRequest request) {
        return Result.success(service.draft(request));
    }
}
