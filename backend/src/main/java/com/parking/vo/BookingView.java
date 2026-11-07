package com.parking.vo;

import com.parking.common.Types.BookingStatus;
import com.parking.common.Types.ResourceType;
import java.math.BigDecimal;
import java.time.Instant;

public record BookingView(
    String id,
    String bookingNo,
    String resourceId,
    String resourceCode,
    String parkName,
    ResourceType type,
    String plateNumber,
    Instant startAt,
    Instant endAt,
    BigDecimal hourlyRate,
    BigDecimal amount,
    BookingStatus status,
    Instant holdUntil,
    Instant paidAt,
    Instant cancelledAt,
    Instant createdAt,
    String paymentStatus,
    boolean canCancel) {}
