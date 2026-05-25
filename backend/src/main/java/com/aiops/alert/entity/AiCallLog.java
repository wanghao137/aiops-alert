package com.aiops.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 调用留痕。每次 LLM 调用前后都会记录这条流水。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务场景：NL2RULE / EVENT_SUMMARY / THRESHOLD / CHAT */
    private String scene;

    private Long modelConfigId;
    private String modelName;

    private String requestPayload;
    private String responsePayload;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer durationMs;

    /** SUCCESS / FAILED */
    private String status;
    private String errorMessage;

    private LocalDateTime createdAt;
}
