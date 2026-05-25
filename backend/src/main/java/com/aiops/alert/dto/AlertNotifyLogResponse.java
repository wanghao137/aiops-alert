package com.aiops.alert.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertNotifyLogResponse {

    private Long id;
    private Long eventId;
    private Long ruleId;
    private Long channelId;
    private String channelType;
    private String receiverValue;
    private String notifyTitle;
    private String notifyContent;
    private String sendStatus;
    private String providerMsgId;
    private String failureReason;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
