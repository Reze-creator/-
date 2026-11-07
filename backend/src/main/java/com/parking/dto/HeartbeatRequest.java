package com.parking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HeartbeatRequest(@NotNull Boolean fault, @Size(max = 200) String message) {}
