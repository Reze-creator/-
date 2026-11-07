package com.parking.dto;

import com.parking.common.Types.EventAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventActionRequest(
    @NotNull EventAction action, @NotBlank @Size(max = 500) String reason) {}
