package com.aiops.alert.controller;

import com.aiops.alert.common.Result;
import com.aiops.alert.service.ai.ThresholdRecommendService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阈值智能推荐：基于历史指标分位数 + 经验值兜底。
 */
@RestController
@RequestMapping("/ai/threshold")
public class ThresholdRecommendController {

    private final ThresholdRecommendService service;

    public ThresholdRecommendController(ThresholdRecommendService service) {
        this.service = service;
    }

    /**
     * 推荐三档阈值（高/中/低敏感）。
     * @param objectId    可选；指定对象时基于该对象的历史
     * @param objectType  必填；用于查找指标元数据
     * @param metricCode  必填；指标编码
     */
    @GetMapping
    public Result<Map<String, Object>> recommend(
            @RequestParam(required = false) Long objectId,
            @RequestParam String objectType,
            @RequestParam String metricCode) {
        return Result.success(service.recommend(objectId, objectType, metricCode));
    }
}
