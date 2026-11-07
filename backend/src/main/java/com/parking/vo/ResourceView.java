package com.parking.vo;

import com.parking.common.Types.ResourceType;
import java.math.BigDecimal;

public record ResourceView(
    String id,
    String code,
    String parkCode,
    String parkName,
    String location,
    ResourceType type,
    int powerKw,
    BigDecimal hourlyRate,
    boolean enabled,
    Boolean available) {}
