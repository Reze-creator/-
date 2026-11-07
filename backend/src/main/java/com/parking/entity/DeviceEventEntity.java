package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** device_event 的持久化对象；仅在数据层与业务实现中使用。 */
@Data
@TableName("device_event")
public class DeviceEventEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String bookingId;
  private String resourceId;
  private Integer sequenceNo;
  private String fingerprint;
  private String eventType;
  private Instant occurredAt;
  private BigDecimal energyKwh;
  private String status;
  private String errorMessage;
  private Instant createdAt;
  private Instant processedAt;
}
