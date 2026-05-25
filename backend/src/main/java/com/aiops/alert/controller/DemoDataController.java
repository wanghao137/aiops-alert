package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.service.support.DemoDataService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoDataController {

    private final DemoDataService service;

    public DemoDataController(DemoDataService service) {
        this.service = service;
    }

    /**
     * 一键填充演示数据：对象 + 渠道 + 规则 + 历史事件 + AI 摘要。
     */
    @PostMapping("/seed")
    public Result<String> seed() {
        return Result.success(service.seed());
    }

    @PostMapping("/clean")
    public Result<String> clean() {
        return Result.success(service.clean());
    }
}
