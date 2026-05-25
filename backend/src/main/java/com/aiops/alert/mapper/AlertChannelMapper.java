package com.aiops.alert.mapper;

import com.aiops.alert.entity.AlertChannel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertChannelMapper extends BaseMapper<AlertChannel> {

    @Select("""
            select channel_type as channelType,
                   count(*) as total,
                   sum(case when status = 'ENABLED' then 1 else 0 end) as enabled
              from alert_channel
             group by channel_type
            """)
    List<Map<String, Object>> countByType();
}
