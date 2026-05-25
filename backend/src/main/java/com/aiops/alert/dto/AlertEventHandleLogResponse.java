package com.aiops.alert.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertEventHandleLogResponse {
    private Long id;
    private Long eventId;
    private String actionType;
    private String actionTypeName;
    private String beforeStatus;
    private String afterStatus;
    private String operatorName;
    private String operatorPhone;
    private String actionComment;
    private LocalDateTime createdAt;
}
