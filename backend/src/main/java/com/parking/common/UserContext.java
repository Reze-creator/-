package com.parking.common;

import com.parking.vo.UserView;
import jakarta.servlet.http.HttpServletRequest;

public final class UserContext {
  private UserContext() {}

  public static final String ATTRIBUTE = "parkingUser";

  public static UserView current(HttpServletRequest request) {
    Object value = request.getAttribute(ATTRIBUTE);
    if (!(value instanceof UserView user)) throw BusinessException.unauthorized();
    return user;
  }
}
