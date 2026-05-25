package com.aiops.alert.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertRuleConditionResponse {
    private Long id;
    private Integer conditionOrder;
    private String metricCode;
    private String metricName;
    private String compareOp;
    private String compareOpLabel;
    private String thresholdValue;
    private String thresholdUnit;
}
