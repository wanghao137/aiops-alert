package com.aiops.alert.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonitorObjectResponse {

    private Long id;
    private String objectCode;
    private String objectName;
    private String objectType;
    private String objectTypeName;
    private String ownerName;
    private String ownerPhone;
    private String tags;
    private String status;
    private String description;
    private String extConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
