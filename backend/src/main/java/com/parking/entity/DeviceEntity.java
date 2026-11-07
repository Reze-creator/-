package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** simulated_device 的持久化对象；仅在数据层与业务实现中使用。 */
@Data
@TableName("simulated_device")
public class DeviceEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private Instant lastSeenAt;
  private Boolean fault;
  private String faultMessage;
  private Instant updatedAt;
}
