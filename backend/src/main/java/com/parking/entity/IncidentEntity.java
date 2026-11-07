package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** operation_incident 的持久化对象；仅在数据层与业务实现中使用。 */
@Data
@TableName("operation_incident")
public class IncidentEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String sourceType;
  private String sourceId;
  private String resourceId;
  private String bookingId;
  private String title;
  private String detail;
  private String status;
  private String resolution;
  private String resolvedBy;
  private Instant createdAt;
  private Instant resolvedAt;
}
