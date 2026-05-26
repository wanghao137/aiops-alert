package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventNo;
    private Long incidentId;
    private Long ruleId;
    private Long objectId;
    private String objectType;
    private String objectName;
    private String metricCode;
    private String metricName;
    private String alertLevel;
    /** PENDING / CONFIRMED / RECOVERED / CLOSED */
    private String eventStatus;
    private String currentValue;
    private String thresholdValue;
    private String eventTitle;
    private String eventContent;
    private String eventReason;
    /** AI 摘要 JSON */
    private String aiSummary;
    /** AI 思考过程 (reasoning_content) */
    private String aiReasoning;
    /** PENDING / SUCCESS / FAILED */
    private String aiSummaryStatus;

    private LocalDateTime firstTriggeredAt;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime recoveredAt;
    private LocalDateTime closedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
