package com.aiops.alert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertChannelTestRequest {

    @NotNull(message = "渠道ID不能为空")
    private Long channelId;

    /** 接收人，如手机号/邮箱列表，逗号分隔；不填则使用渠道默认配置 */
    private String receiverValue;

    private String title;
    private String content;
}
