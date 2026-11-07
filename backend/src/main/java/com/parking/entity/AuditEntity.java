package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** operation_audit 的持久化对象；仅在数据层与业务实现中使用。 */
@Data
@TableName("operation_audit")
public class AuditEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String actorId;
  private String action;
  private String targetId;
  private String detail;
  private String requestId;
  private Instant createdAt;
}
