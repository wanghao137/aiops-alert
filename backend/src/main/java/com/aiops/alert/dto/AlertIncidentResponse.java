package com.aiops.alert.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertIncidentResponse {
    private Long id;
    private String incidentNo;
    private Long objectId;
    private String objectType;
    private String objectName;
    private String topLevel;
    private Integer eventCount;
    private String status;
    private String summary;
    private LocalDateTime firstEventAt;
    private LocalDateTime lastEventAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AlertEventResponse> events;
}
