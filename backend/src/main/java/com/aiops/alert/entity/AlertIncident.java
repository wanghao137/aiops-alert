package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 告警归并组（Incident）。
 * 同对象在合并窗口内的多条告警自动汇总成一个 Incident，便于运维聚焦故障。
 */
@Data
@TableName("alert_incident")
public class AlertIncident {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String incidentNo;
    private Long objectId;
    private String objectType;
    private String objectName;

    /** 当前最高级别 NOTICE / NORMAL / SERIOUS / CRITICAL */
    private String topLevel;

    private Integer eventCount;

    /** OPEN / CLOSED */
    private String status;

    /** AI 生成的 Incident 简报 */
    private String summary;

    private LocalDateTime firstEventAt;
    private LocalDateTime lastEventAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
