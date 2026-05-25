package com.aiops.alert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertNotifyRetryRequest {

    @NotNull(message = "通知记录ID不能为空")
    private Long notifyLogId;
}
