package com.parking.vo;

import java.time.Instant;

public record LoginView(String token, Instant expiresAt, UserView user) {}
