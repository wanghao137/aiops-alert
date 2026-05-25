package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("alert_event_handle_log")
public class AlertEventHandleLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String actionType;
    private String beforeStatus;
    private String afterStatus;
    private String operatorName;
    private String operatorPhone;
    private String actionComment;
    private LocalDateTime createdAt;
}
