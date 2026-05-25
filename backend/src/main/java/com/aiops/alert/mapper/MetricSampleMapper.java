package com.aiops.alert.mapper;

import com.aiops.alert.entity.MetricSample;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetricSampleMapper extends BaseMapper<MetricSample> {
}
