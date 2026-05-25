package com.aiops.alert.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmTestResponse {
    /** 是否成功 */
    private Boolean success;
    /** 模型回声内容（成功时） */
    private String reply;
    /** 失败原因（失败时） */
    private String error;
    private Integer durationMs;
    private String modelName;
}
