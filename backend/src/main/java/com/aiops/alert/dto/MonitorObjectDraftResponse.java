package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonitorObjectDraftResponse {

    private MonitorObjectRequest draft;
    private String understanding;
    private List<String> warnings;
    private Integer durationMs;
    private String modelName;
    private String reasoning;
}
