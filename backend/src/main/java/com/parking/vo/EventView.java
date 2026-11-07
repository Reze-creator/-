package com.parking.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record EventView(
    String id,
    String bookingId,
    int sequenceNo,
    String type,
    Instant occurredAt,
    BigDecimal energyKwh,
    String status,
    String errorMessage,
    Instant createdAt) {}
