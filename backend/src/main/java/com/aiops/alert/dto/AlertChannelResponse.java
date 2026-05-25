package com.aiops.alert.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertChannelResponse {

    private Long id;
    private String channelCode;
    private String channelName;
    private String channelType;
    private String channelTypeName;
    private String providerName;
    private String status;
    private Integer priority;
    private String configJson;
    private String description;

    /** 最近一次发送结果（用于卡片展示） */
    private String lastSendStatus;
    private String lastFailureReason;
    private LocalDateTime lastSentAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
