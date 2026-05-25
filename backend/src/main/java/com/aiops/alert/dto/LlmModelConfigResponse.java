package com.aiops.alert.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmModelConfigResponse {

    private Long id;
    private String configCode;
    private String configName;
    private String provider;
    private String baseUrl;
    /** 已脱敏 */
    private String apiKeyMasked;
    private String modelName;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean isDefault;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
