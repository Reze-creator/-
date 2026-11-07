package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("booking")
public class BookingEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String bookingNo;
  private String userId;
  private String requestId;
  private String fingerprint;
  private String resourceId;
  private String resourceCode;
  private String parkName;
  private String resourceType;
  private String plateNumber;
  private Instant startAt;
  private Instant endAt;
  private BigDecimal hourlyRate;
  private BigDecimal amount;
  private String status;
  private Instant holdUntil;
  private Instant paidAt;
  private Instant cancelledAt;
  private Instant createdAt;
  private Instant updatedAt;
  private Integer lastEventSequence;
}
