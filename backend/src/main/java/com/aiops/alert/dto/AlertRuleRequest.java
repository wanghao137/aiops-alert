package com.aiops.alert.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class AlertRuleRequest {

    private Long id;

    @Size(max = 64)
    private String ruleCode;

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128)
    private String ruleName;

    @NotBlank(message = "对象类型不能为空")
    private String objectType;

    /** AND / OR，默认 AND */
    private String conditionLogic;

    private Integer triggerTimes;
    private Integer timeWindowMinutes;
    private Integer minAlertIntervalMinutes;

    @NotBlank(message = "告警级别不能为空")
    private String alertLevel;

    private Boolean recoverNotify;
    private Boolean repeatNotify;

    private String status;
    private Integer priority;

    private String notifyTitleTemplate;
    private String notifyContentTemplate;
    private String description;

    @Valid
    @NotEmpty(message = "至少配置一个触发条件")
    private List<AlertRuleConditionDto> conditions;

    @NotEmpty(message = "至少选择一个监控对象")
    private List<Long> objectIds;

    @Valid
    private List<AlertRuleChannelBindingDto> channelBindings;
}
