package com.aiops.alert.controller;

import cn.hutool.core.util.StrUtil;
import com.aiops.alert.common.Result;
import com.aiops.alert.dto.AlertIncidentResponse;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertIncident;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertIncidentMapper;
import com.aiops.alert.service.core.AlertEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alert-incidents")
public class AlertIncidentController {

    private final AlertIncidentMapper incidentMapper;
    private final AlertEventMapper eventMapper;
    private final AlertEventService eventService;

    public AlertIncidentController(AlertIncidentMapper incidentMapper,
                                   AlertEventMapper eventMapper,
                                   AlertEventService eventService) {
        this.incidentMapper = incidentMapper;
        this.eventMapper = eventMapper;
        this.eventService = eventService;
    }

    @GetMapping
    public Result<List<AlertIncidentResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String objectType) {
        List<AlertIncident> incidents = incidentMapper.selectList(new LambdaQueryWrapper<AlertIncident>()
                .eq(StrUtil.isNotBlank(status), AlertIncident::getStatus, status)
                .eq(StrUtil.isNotBlank(objectType), AlertIncident::getObjectType, objectType)
                .orderByDesc(AlertIncident::getLastEventAt));
        if (incidents.isEmpty()) return Result.success(List.of());

        List<Long> ids = incidents.stream().map(AlertIncident::getId).toList();
        Map<Long, List<AlertEvent>> grouped = eventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .in(AlertEvent::getIncidentId, ids)
                .orderByDesc(AlertEvent::getLastTriggeredAt))
                .stream().collect(Collectors.groupingBy(AlertEvent::getIncidentId));

        return Result.success(incidents.stream().map(i -> AlertIncidentResponse.builder()
                .id(i.getId())
                .incidentNo(i.getIncidentNo())
                .objectId(i.getObjectId())
                .objectType(i.getObjectType())
                .objectName(i.getObjectName())
                .topLevel(i.getTopLevel())
                .eventCount(i.getEventCount())
                .status(i.getStatus())
                .summary(i.getSummary())
                .firstEventAt(i.getFirstEventAt())
                .lastEventAt(i.getLastEventAt())
                .closedAt(i.getClosedAt())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .events(grouped.getOrDefault(i.getId(), List.of()).stream()
                        .map(e -> eventService.get(e.getId())).collect(Collectors.toList()))
                .build()
        ).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public Result<AlertIncidentResponse> get(@PathVariable Long id) {
        AlertIncident i = incidentMapper.selectById(id);
        if (i == null) return Result.success(null);
        List<AlertEvent> events = eventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getIncidentId, id)
                .orderByDesc(AlertEvent::getLastTriggeredAt));
        return Result.success(AlertIncidentResponse.builder()
                .id(i.getId())
                .incidentNo(i.getIncidentNo())
                .objectId(i.getObjectId())
                .objectType(i.getObjectType())
                .objectName(i.getObjectName())
                .topLevel(i.getTopLevel())
                .eventCount(i.getEventCount())
                .status(i.getStatus())
                .summary(i.getSummary())
                .firstEventAt(i.getFirstEventAt())
                .lastEventAt(i.getLastEventAt())
                .closedAt(i.getClosedAt())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .events(events.stream().map(e -> eventService.get(e.getId())).collect(Collectors.toList()))
                .build());
    }
}
