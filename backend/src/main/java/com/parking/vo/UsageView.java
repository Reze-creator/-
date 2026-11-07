package com.parking.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageView(
    String id,
    String bookingId,
    String resourceCode,
    String parkName,
    String type,
    String plateNumber,
    String status,
    Instant enteredAt,
    Instant chargingStartedAt,
    Instant chargingStoppedAt,
    Instant exitedAt,
    BigDecimal energyKwh,
    BigDecimal energyRate,
    int lastSequence) {}
