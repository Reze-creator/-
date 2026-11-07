package com.parking.vo;

import java.time.Instant;

public record IncidentView(
    String id,
    String sourceType,
    String sourceId,
    String resourceId,
    String bookingId,
    String title,
    String detail,
    String status,
    String resolution,
    Instant createdAt,
    Instant resolvedAt) {}
