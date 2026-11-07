package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,32}") String username,
    @NotBlank @Size(min = 8, max = 64) String password) {}
