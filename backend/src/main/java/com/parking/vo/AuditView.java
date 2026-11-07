package com.parking.vo;

import java.time.Instant;

public record AuditView(
    String id,
    String actorId,
    String action,
    String targetId,
    String detail,
    String requestId,
    Instant createdAt) {}
