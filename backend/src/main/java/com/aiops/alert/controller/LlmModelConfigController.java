package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.dto.LlmModelConfigRequest;
import com.aiops.alert.dto.LlmModelConfigResponse;
import com.aiops.alert.service.ai.LlmModelConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/llm-configs")
public class LlmModelConfigController {

    private final LlmModelConfigService service;

    public LlmModelConfigController(LlmModelConfigService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<LlmModelConfigResponse>> list() {
        return Result.success(service.list());
    }

    @PostMapping
    public Result<LlmModelConfigResponse> save(@Valid @RequestBody LlmModelConfigRequest request) {
        return Result.success(service.save(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/default")
    public Result<LlmModelConfigResponse> setDefault(@PathVariable Long id) {
        return Result.success(service.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public Result<com.aiops.alert.dto.LlmTestResponse> test(@PathVariable Long id) {
        return Result.success(service.test(id));
    }
}
