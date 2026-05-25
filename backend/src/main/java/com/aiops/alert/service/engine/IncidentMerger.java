package com.aiops.alert.service.engine;

import com.aiops.alert.common.Enums;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.entity.AlertIncident;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.AlertIncidentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 告警归并：把同一 object 在窗口内的多个事件合并到一个 Incident。
 *
 * 规则：
 *  - 优先匹配最近一次 last_event_at 在 mergeWindowMinutes 内的 OPEN incident（按 object_id）
 *  - 命中：把事件挂上去，更新 eventCount / topLevel / lastEventAt
 *  - 未命中：新建 incident
 */
@Slf4j
@Service
public class IncidentMerger {

    private final AlertIncidentMapper incidentMapper;

    @Value("${aiops.incident.merge-window-minutes:30}")
    private int mergeWindowMinutes;

    public IncidentMerger(AlertIncidentMapper incidentMapper) {
        this.incidentMapper = incidentMapper;
    }

    /**
     * 给事件分配 incident_id（不更新事件本身）。返回 incident。
     */
    public AlertIncident assign(AlertEvent event, MonitorObject object) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(mergeWindowMinutes);
        AlertIncident exist = incidentMapper.selectOne(new LambdaQueryWrapper<AlertIncident>()
                .eq(AlertIncident::getObjectId, event.getObjectId())
                .eq(AlertIncident::getStatus, "OPEN")
                .ge(AlertIncident::getLastEventAt, windowStart)
                .orderByDesc(AlertIncident::getLastEventAt)
                .last("limit 1"));

        if (exist != null) {
            exist.setEventCount(exist.getEventCount() == null ? 1 : exist.getEventCount() + 1);
            exist.setLastEventAt(event.getLastTriggeredAt() == null
                    ? LocalDateTime.now() : event.getLastTriggeredAt());
            if (compareLevel(event.getAlertLevel(), exist.getTopLevel()) > 0) {
                exist.setTopLevel(event.getAlertLevel());
            }
            incidentMapper.updateById(exist);
            return exist;
        }

        AlertIncident incident = new AlertIncident();
        incident.setIncidentNo("INC-" + LocalDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        incident.setObjectId(event.getObjectId());
        incident.setObjectType(event.getObjectType());
        incident.setObjectName(event.getObjectName());
        incident.setTopLevel(event.getAlertLevel());
        incident.setEventCount(1);
        incident.setStatus("OPEN");
        incident.setFirstEventAt(event.getFirstTriggeredAt() == null
                ? LocalDateTime.now() : event.getFirstTriggeredAt());
        incident.setLastEventAt(incident.getFirstEventAt());
        incidentMapper.insert(incident);
        return incident;
    }

    /**
     * 当事件全部恢复后，关闭 incident。这里简单实现：传入 object 的 incident，如果传入 events
     * 都已 RECOVERED/CLOSED，就关闭。
     */
    public void closeIfAllRecovered(Long incidentId, List<AlertEvent> events) {
        if (incidentId == null || events == null || events.isEmpty()) return;
        boolean allDone = events.stream().allMatch(e ->
                Enums.EventStatus.RECOVERED.equals(e.getEventStatus())
                        || Enums.EventStatus.CLOSED.equals(e.getEventStatus()));
        if (!allDone) return;
        AlertIncident incident = incidentMapper.selectById(incidentId);
        if (incident == null || "CLOSED".equals(incident.getStatus())) return;
        incident.setStatus("CLOSED");
        incident.setClosedAt(LocalDateTime.now());
        incidentMapper.updateById(incident);
    }

    private int compareLevel(String a, String b) {
        return weight(a) - weight(b);
    }

    private int weight(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "NOTICE" -> 1;
            case "NORMAL" -> 2;
            case "SERIOUS" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }
}
