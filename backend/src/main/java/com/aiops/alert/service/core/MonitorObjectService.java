package com.aiops.alert.service.core;

import com.aiops.alert.common.BizException;
import com.aiops.alert.common.Enums;
import com.aiops.alert.dto.MonitorObjectRequest;
import com.aiops.alert.dto.MonitorObjectResponse;
import com.aiops.alert.dto.MonitorObjectStatsResponse;
import com.aiops.alert.entity.MonitorObject;
import com.aiops.alert.mapper.MonitorObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 监控对象服务。
 */
@Service
public class MonitorObjectService {

    private final MonitorObjectMapper mapper;

    public MonitorObjectService(MonitorObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<MonitorObjectResponse> list(String objectType, String keyword, String status) {
        LambdaQueryWrapper<MonitorObject> wrapper = new LambdaQueryWrapper<MonitorObject>()
                .eq(StringUtils.hasText(objectType), MonitorObject::getObjectType, objectType)
                .eq(StringUtils.hasText(status), MonitorObject::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MonitorObject::getObjectName, keyword)
                        .or().like(MonitorObject::getObjectCode, keyword)
                        .or().like(MonitorObject::getTags, keyword))
                .orderByDesc(MonitorObject::getUpdatedAt)
                .orderByDesc(MonitorObject::getId);
        return mapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MonitorObjectResponse get(Long id) {
        MonitorObject entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException("监控对象不存在");
        }
        return toResponse(entity);
    }

    public MonitorObjectResponse save(MonitorObjectRequest request) {
        if (!Enums.ObjectType.isValid(request.getObjectType())) {
            throw new BizException("对象类型不合法：" + request.getObjectType());
        }

        MonitorObject entity;
        if (request.getId() == null) {
            entity = new MonitorObject();
        } else {
            entity = mapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException("监控对象不存在");
            }
        }

        // 编码处理：未填则自动生成；新建时检查唯一
        String code = StringUtils.hasText(request.getObjectCode())
                ? request.getObjectCode().trim()
                : "OBJ-" + shortId();
        if (entity.getId() == null || !code.equals(entity.getObjectCode())) {
            Long count = mapper.selectCount(new LambdaQueryWrapper<MonitorObject>()
                    .eq(MonitorObject::getObjectCode, code)
                    .ne(entity.getId() != null, MonitorObject::getId, entity.getId()));
            if (count != null && count > 0) {
                throw new BizException("对象编码已存在：" + code);
            }
        }

        entity.setObjectCode(code);
        entity.setObjectName(request.getObjectName().trim());
        entity.setObjectType(request.getObjectType());
        entity.setOwnerName(request.getOwnerName());
        entity.setOwnerPhone(request.getOwnerPhone());
        entity.setTags(request.getTags());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : Enums.Status.ENABLED);
        entity.setDescription(request.getDescription());
        entity.setExtConfig(request.getExtConfig());

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toResponse(entity);
    }

    public MonitorObjectResponse toggleStatus(Long id) {
        MonitorObject entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException("监控对象不存在");
        }
        entity.setStatus(Enums.Status.ENABLED.equals(entity.getStatus())
                ? Enums.Status.DISABLED
                : Enums.Status.ENABLED);
        mapper.updateById(entity);
        return toResponse(entity);
    }

    public void delete(Long id) {
        MonitorObject entity = mapper.selectById(id);
        if (entity == null) {
            return;
        }
        mapper.deleteById(id);
    }

    public MonitorObjectStatsResponse stats() {
        long total = mapper.selectCount(null);
        long enabled = mapper.selectCount(new LambdaQueryWrapper<MonitorObject>()
                .eq(MonitorObject::getStatus, Enums.Status.ENABLED));

        List<Map<String, Object>> rows = mapper.countByType();
        List<MonitorObjectStatsResponse.TypeStat> byType = new ArrayList<>();
        for (String type : Enums.ObjectType.ALL) {
            long t = 0;
            long e = 0;
            for (Map<String, Object> row : rows) {
                if (type.equals(row.get("objectType"))) {
                    t = toLong(row.get("total"));
                    e = toLong(row.get("enabled"));
                    break;
                }
            }
            byType.add(MonitorObjectStatsResponse.TypeStat.builder()
                    .objectType(type)
                    .objectTypeName(typeName(type))
                    .total(t)
                    .enabled(e)
                    .build());
        }

        return MonitorObjectStatsResponse.builder()
                .total(total)
                .enabled(enabled)
                .byType(byType)
                .build();
    }

    // ---------------- helpers ----------------

    private MonitorObjectResponse toResponse(MonitorObject entity) {
        return MonitorObjectResponse.builder()
                .id(entity.getId())
                .objectCode(entity.getObjectCode())
                .objectName(entity.getObjectName())
                .objectType(entity.getObjectType())
                .objectTypeName(typeName(entity.getObjectType()))
                .ownerName(entity.getOwnerName())
                .ownerPhone(entity.getOwnerPhone())
                .tags(entity.getTags())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .extConfig(entity.getExtConfig())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String typeName(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "SERVER" -> "服务器";
            case "DATABASE" -> "数据库";
            case "SYNC_JOB" -> "数据同步作业";
            case "PROCESS_JOB" -> "数据加工作业";
            default -> type;
        };
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
