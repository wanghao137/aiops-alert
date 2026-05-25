package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * NL2Rule 返回结果。包含 AI 解析出的草稿规则 + 思路说明 + 警告。
 */
@Data
@Builder
public class NlRuleDraftResponse {

    private AlertRuleRequest draft;

    /** AI 简短的"理解"说明，用于让用户判断是否符合预期。 */
    private String understanding;

    /** AI 自检后的提示，例如缺渠道、对象不匹配等。 */
    private List<String> warnings;

    /** AI 调用耗时 (ms) */
    private Integer durationMs;
    private String modelName;
}
