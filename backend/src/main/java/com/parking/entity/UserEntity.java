package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("app_user")
public class UserEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String username;
  private String passwordHash;
  private String displayName;
  private String role;
  private Instant createdAt;
}
