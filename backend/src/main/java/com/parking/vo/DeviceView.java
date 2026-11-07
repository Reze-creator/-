package com.parking.vo;

import java.time.Instant;

public record DeviceView(
    String id,
    String resourceCode,
    String parkName,
    String type,
    String status,
    Instant lastSeenAt,
    String faultMessage) {}
