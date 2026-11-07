package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** usage_bill 的持久化对象；仅在数据层与业务实现中使用。 */
@Data
@TableName("usage_bill")
public class UsageBillEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String bookingId;
  private String sessionId;
  private String userId;
  private Long parkingMinutes;
  private BigDecimal parkingAmount;
  private BigDecimal energyKwh;
  private BigDecimal energyRate;
  private BigDecimal energyAmount;
  private BigDecimal totalAmount;
  private BigDecimal prepaidAmount;
  private BigDecimal additionalAmount;
  private BigDecimal refundAmount;
  private String status;
  private Instant createdAt;
}
