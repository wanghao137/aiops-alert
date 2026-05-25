package com.aiops.alert.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertEventResponse {

    private Long id;
    private String eventNo;
    private Long incidentId;
    private Long ruleId;
    private String ruleName;
    private Long objectId;
    private String objectType;
    private String objectName;
    private String metricCode;
    private String metricName;
    private String alertLevel;
    private String eventStatus;
    private String currentValue;
    private String thresholdValue;
    private String eventTitle;
    private String eventContent;
    private String eventReason;

    /** AI 摘要：what / impact / causes / actions */
    private String aiSummary;
    private String aiSummaryStatus;

    private LocalDateTime firstTriggeredAt;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime recoveredAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<AlertEventHandleLogResponse> handleLogs;
    private List<AlertNotifyLogResponse> notifyLogs;
}
