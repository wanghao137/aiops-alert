package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class LlmModelConfigRequest {

    private Long id;

    @Size(max = 64)
    private String configCode;

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 128)
    private String configName;

    private String provider;

    @NotBlank(message = "base URL 不能为空")
    private String baseUrl;

    private String apiKey;

    @NotBlank(message = "模型名不能为空")
    private String modelName;

    private BigDecimal temperature;
    private Integer maxTokens;

    private Boolean isDefault;
    private String status;
    private String description;
}
