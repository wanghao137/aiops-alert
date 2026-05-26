package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.service.engine.MetricSimulator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标模拟器控制台。演示时强制让某对象某指标进入异常状态，
 * 通常 30-60 秒后规则引擎就会触发告警事件。
 */
@RestController
@RequestMapping("/simulator")
public class SimulatorController {

    private final MetricSimulator simulator;

    public SimulatorController(MetricSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/force-story")
    public Result<String> forceStory(@RequestParam Long objectId,
                                     @RequestParam String metricCode) {
        simulator.forceStory(objectId, metricCode);
        return Result.success(
                "已让该对象的 " + metricCode + " 进入异常状态，约 30-60 秒后会触发告警");
    }
}
