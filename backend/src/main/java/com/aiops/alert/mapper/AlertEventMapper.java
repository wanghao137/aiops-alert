package com.aiops.alert.mapper;

import com.aiops.alert.entity.AlertEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    @Select("""
            select id, first_triggered_at, event_status, alert_level
              from alert_event
             where first_triggered_at >= #{since}
            """)
    List<Map<String, Object>> trendRowsSince(@Param("since") String since);

    @Select("""
            select event_status as code, count(*) as total
              from alert_event
             group by event_status
            """)
    List<Map<String, Object>> countByStatus();

    @Select("""
            select alert_level as code, count(*) as total
              from alert_event
             group by alert_level
            """)
    List<Map<String, Object>> countByLevel();

    @Select("""
            select object_type as code, count(*) as total
              from alert_event
             group by object_type
            """)
    List<Map<String, Object>> countByObjectType();

    @Select("""
            select rule_id as ruleId, count(*) as hitCount
              from alert_event
             where first_triggered_at >= #{since}
             group by rule_id
             order by hitCount desc
             limit 8
            """)
    List<Map<String, Object>> ruleHitTop(@Param("since") String since);
}
