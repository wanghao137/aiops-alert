package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("alert_rule_channel_rel")
public class AlertRuleChannelRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;
    private Long channelId;
    private String receiverValue;
    private String templateCode;

    private LocalDateTime createdAt;
}
