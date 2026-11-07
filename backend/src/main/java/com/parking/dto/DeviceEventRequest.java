package com.parking.dto;

import com.parking.common.Types.DeviceEventType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

/** energyKwh 是本次会话的累计电量，不是单个事件的增量。 */
public record DeviceEventRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String eventId,
    @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String bookingId,
    @Min(1) @Max(10000) int sequenceNo,
    @NotNull DeviceEventType type,
    @NotNull Instant occurredAt,
    @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 3)
        BigDecimal energyKwh) {}
