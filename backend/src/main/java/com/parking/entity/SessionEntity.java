package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("user_session")
public class SessionEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String userId;
  private Instant expiresAt;
  private Instant createdAt;
}
