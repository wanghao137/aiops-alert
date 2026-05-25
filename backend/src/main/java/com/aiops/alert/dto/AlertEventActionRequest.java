package com.aiops.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertEventActionRequest {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    /** CONFIRM / RECOVER / CLOSE / COMMENT */
    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    private String operatorName;
    private String operatorPhone;
    private String actionComment;
}
