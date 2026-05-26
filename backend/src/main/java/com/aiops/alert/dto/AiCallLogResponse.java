package com.aiops.alert.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * AiCallLog 行视图。
 *
 * 列表接口（page / slowTop）只返回 id / scene / modelConfigId / modelName / promptTokens /
 * completionTokens / totalTokens / durationMs / status / errorMessage / estimatedCost / createdAt 12 列；
 * 详情接口（getById）额外返回 reasoningContent / requestPayload / responsePayload。
 */
@Data
@Builder
public class AiCallLogResponse {
    private Long id;
    private String scene;
    private Long modelConfigId;
    private String modelName;

    private Integer promptTokens;
    private Integer completionTokens;
    /** 派生：promptTokens + completionTokens */
    private Integer totalTokens;
    private Integer durationMs;

    private String status;
    private String errorMessage;

    /** 仅详情接口返回 */
    private String reasoningContent;
    /** 仅详情接口返回 */
    private String requestPayload;
    /** 仅详情接口返回 */
    private String responsePayload;

    /** 派生：基于 LlmModelConfig 单价计算的估算成本（元） */
    private BigDecimal estimatedCost;

    private LocalDateTime createdAt;
}
