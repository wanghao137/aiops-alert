package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 告警规则。
 */
@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;
    private String ruleName;
    private String objectType;

    /** AND / OR */
    private String conditionLogic;

    private Integer triggerTimes;
    private Integer timeWindowMinutes;
    private Integer minAlertIntervalMinutes;

    /** NOTICE / NORMAL / SERIOUS / CRITICAL */
    private String alertLevel;

    private Integer recoverNotify;
    private Integer repeatNotify;
    private String status;
    private Integer priority;

    private String notifyTitleTemplate;
    private String notifyContentTemplate;
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
