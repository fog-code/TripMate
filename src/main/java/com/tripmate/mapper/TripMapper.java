package com.tripmate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tripmate.entity.Trip;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TripMapper extends BaseMapper<Trip> {
}
