package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlertChannelRequest {

    private Long id;

    @Size(max = 64)
    private String channelCode;

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 128)
    private String channelName;

    @NotBlank(message = "渠道类型不能为空")
    private String channelType;

    private String providerName;
    private String status;
    private Integer priority;

    /** 配置 JSON 字符串 */
    private String configJson;

    private String description;
}
