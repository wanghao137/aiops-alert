package com.aiops.alert.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * AiCallLog 流水分页查询参数。
 */
@Data
public class AiCallLogQuery {
    private String scene;
    private String modelName;
    private String status;

    @Min(value = 1, message = "page 至少为 1")
    private Integer page = 1;

    @Min(value = 1, message = "size 至少为 1")
    @Max(value = 100, message = "size 至多为 100")
    private Integer size = 20;
}
