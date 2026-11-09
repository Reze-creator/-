package com.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.parking.entity.IncidentEntity;

public interface IncidentMapper extends BaseMapper<IncidentEntity> {
  /** 同一异常源重复扫描只保留一张工单；故障复发时重新打开原工单。 */
  @org.apache.ibatis.annotations.Insert(
      """
      INSERT INTO operation_incident(id,source_type,source_id,resource_id,booking_id,title,detail,status,created_at)
      VALUES(#{id},#{sourceType},#{sourceId},#{resourceId},#{bookingId},#{title},#{detail},'OPEN',#{createdAt})
      ON DUPLICATE KEY UPDATE title=VALUES(title),detail=VALUES(detail),status='OPEN',
        resolution=NULL,resolved_by=NULL,resolved_at=NULL,search_version=search_version+1
      """)
  void reopen(IncidentEntity incident);
}
