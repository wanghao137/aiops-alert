package com.aiops.alert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 规则与渠道的绑定关系 + 接收人。
 */
@Data
public class AlertRuleChannelBindingDto {

    @NotNull(message = "渠道ID不能为空")
    private Long channelId;

    private String receiverValue;
    private String templateCode;
}
