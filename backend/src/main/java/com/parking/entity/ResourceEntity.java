package com.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("parking_resource")
public class ResourceEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String code;
  private String parkCode;
  private String parkName;
  private String location;
  private String resourceType;
  private Integer powerKw;
  private BigDecimal hourlyRate;
  private Boolean enabled;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String description;
  private Long searchVersion;
  private Instant createdAt;
}
