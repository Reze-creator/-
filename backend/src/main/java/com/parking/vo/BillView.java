package com.parking.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record BillView(
    String id,
    String bookingId,
    String sessionId,
    String resourceCode,
    long parkingMinutes,
    BigDecimal parkingAmount,
    BigDecimal energyKwh,
    BigDecimal energyRate,
    BigDecimal energyAmount,
    BigDecimal totalAmount,
    BigDecimal prepaidAmount,
    BigDecimal additionalAmount,
    BigDecimal refundAmount,
    String status,
    Instant createdAt) {}
