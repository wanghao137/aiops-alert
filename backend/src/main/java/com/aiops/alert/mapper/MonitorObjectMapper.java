package com.aiops.alert.mapper;

import com.aiops.alert.entity.MonitorObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MonitorObjectMapper extends BaseMapper<MonitorObject> {

    /** 按对象类型分组统计：返回 [{object_type, total, enabled}] */
    @Select("""
            select object_type as objectType,
                   count(*) as total,
                   sum(case when status = 'ENABLED' then 1 else 0 end) as enabled
              from monitor_object
             group by object_type
            """)
    List<Map<String, Object>> countByType();
}
