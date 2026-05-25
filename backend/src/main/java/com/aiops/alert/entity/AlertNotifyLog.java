package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 告警通知发送流水。
 */
@Data
@TableName("alert_notify_log")
public class AlertNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;
    private Long ruleId;
    private Long channelId;
    private String channelType;
    private String receiverValue;
    private String notifyTitle;
    private String notifyContent;
    /** PENDING / SUCCESS / FAILED */
    private String sendStatus;
    private String providerMsgId;
    private String failureReason;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
