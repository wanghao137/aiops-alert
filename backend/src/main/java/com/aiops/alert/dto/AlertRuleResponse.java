package com.aiops.alert.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertRuleResponse {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String objectType;
    private String objectTypeName;
    private String conditionLogic;

    private Integer triggerTimes;
    private Integer timeWindowMinutes;
    private Integer minAlertIntervalMinutes;

    private String alertLevel;
    private String alertLevelName;

    private Boolean recoverNotify;
    private Boolean repeatNotify;
    private String status;
    private Integer priority;

    private String notifyTitleTemplate;
    private String notifyContentTemplate;
    private String description;

    private List<AlertRuleConditionDto> conditions;
    private List<Long> objectIds;
    private List<ObjectBrief> objects;
    private List<AlertRuleChannelBindingDto> channelBindings;
    private List<ChannelBrief> channels;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class ObjectBrief {
        private Long id;
        private String objectName;
        private String objectCode;
        private String objectType;
        private String status;
    }

    @Data
    @Builder
    public static class ChannelBrief {
        private Long id;
        private String channelName;
        private String channelType;
        private String channelTypeName;
        private String status;
        private String receiverValue;
    }
}
