package com.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.parking.entity.ResourceEntity;

public interface ResourceMapper extends BaseMapper<ResourceEntity> {
  @org.apache.ibatis.annotations.Select(
      "SELECT * FROM parking_resource WHERE id = #{id} FOR UPDATE")
  ResourceEntity lock(String id);
}
