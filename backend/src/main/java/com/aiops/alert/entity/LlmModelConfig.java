package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * LLM 模型配置（OpenAI 兼容协议）。
 */
@Data
@TableName("llm_model_config")
public class LlmModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configCode;
    private String configName;

    /** OPENAI / QWEN / DEEPSEEK / CUSTOM */
    private String provider;

    private String baseUrl;
    private String apiKey;
    private String modelName;

    private BigDecimal temperature;
    private Integer maxTokens;

    /** 是否默认配置：0/1 */
    private Integer isDefault;

    private String status;
    private String description;

    /** Prompt 单价（元/1k token），用于 AI 调用统计页成本估算，可空 */
    private BigDecimal promptPricePer1k;

    /** Completion 单价（元/1k token），用于 AI 调用统计页成本估算，可空 */
    private BigDecimal completionPricePer1k;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
