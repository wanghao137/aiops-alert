package com.aiops.alert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertEventTestRequest {

    @NotNull(message = "规则ID不能为空")
    private Long ruleId;

    @NotNull(message = "对象ID不能为空")
    private Long objectId;

    private String currentValue;
    private String eventReason;
}
