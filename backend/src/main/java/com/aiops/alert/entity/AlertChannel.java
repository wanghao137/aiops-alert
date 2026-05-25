package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 告警通知渠道。
 */
@Data
@TableName("alert_channel")
public class AlertChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelCode;
    private String channelName;
    /** WECOM / EMAIL / SMS */
    private String channelType;
    private String providerName;
    private String status;
    private Integer priority;
    /** 渠道配置 JSON 字符串：webhook / smtp / accessKey 等 */
    private String configJson;
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
