package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 规则触发条件（一个规则有多个条件）。
 */
@Data
@TableName("alert_rule_condition")
public class AlertRuleCondition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;
    private Integer conditionOrder;
    private String metricCode;
    private String metricName;
    /** GT / GE / LT / LE / EQ / NE / OFFLINE / FAILED / TIMEOUT / IN */
    private String compareOp;
    private String thresholdValue;
    private String thresholdUnit;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
