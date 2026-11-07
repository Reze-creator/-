package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** usage_session 的持久化对象；仅在数据层与业务实现中使用。 */
@Data
@TableName("usage_session")
public class UsageSessionEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String bookingId;
  private String resourceId;
  private String userId;
  private String status;
  private Instant enteredAt;
  private Instant chargingStartedAt;
  private Instant chargingStoppedAt;
  private Instant exitedAt;
  private Instant lastEventAt;
  private BigDecimal energyKwh;
  private BigDecimal energyRate;
  private Instant createdAt;
  private Instant updatedAt;
}
