package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("alert_rule_object_rel")
public class AlertRuleObjectRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;
    private Long objectId;

    private LocalDateTime createdAt;
}
