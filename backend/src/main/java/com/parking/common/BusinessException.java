package com.parking.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public BusinessException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static BusinessException invalid(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  public static BusinessException missing(String message) {
    return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  public static BusinessException conflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "CONFLICT", message);
  }

  public static BusinessException unauthorized() {
    return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效，请重新登录");
  }

  public static BusinessException forbidden() {
    return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有操作权限");
  }
}
