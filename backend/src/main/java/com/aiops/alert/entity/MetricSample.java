package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("metric_sample")
public class MetricSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long objectId;
    private String metricCode;
    private String metricValue;
    private BigDecimal numericValue;
    private LocalDateTime sampledAt;
}
