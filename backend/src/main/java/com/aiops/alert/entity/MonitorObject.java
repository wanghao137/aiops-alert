package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 监控对象。
 */
@Data
@TableName("monitor_object")
public class MonitorObject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String objectCode;
    private String objectName;
    private String objectType;
    private String ownerName;
    private String ownerPhone;
    private String tags;
    private String status;
    private String description;

    /** 扩展配置 JSON 文本：IP/端口/作业编码 等差异化属性 */
    private String extConfig;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
