package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record CreateBookingRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String requestId,
    @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String resourceId,
    @NotBlank @Pattern(regexp = "[\\p{IsHan}a-zA-Z0-9]{5,10}") String plateNumber,
    @NotNull Instant startAt,
    @NotNull Instant endAt) {}
