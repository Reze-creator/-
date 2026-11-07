package com.parking.dto;

import jakarta.validation.constraints.NotNull;

public record ResourceStatusRequest(@NotNull Boolean enabled) {}
