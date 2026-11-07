package com.parking.vo;

import com.parking.common.Types.Role;

public record UserView(String id, String username, String displayName, Role role) {}
