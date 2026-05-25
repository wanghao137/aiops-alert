package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlertRuleConditionRequest {

    private Long id;
    private Integer conditionOrder;

    @NotBlank(message = "指标编码不能为空")
    private String metricCode;

    @NotBlank(message = "指标名称不能为空")
    private String metricName;

    @NotBlank(message = "比较符不能为空")
    private String compareOp;

    private String thresholdValue;
    private String thresholdUnit;
}
