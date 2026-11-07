package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("mock_payment")
public class PaymentEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String bookingId;
  private BigDecimal amount;
  private String status;
  private Instant paidAt;
  private Instant refundedAt;
}
