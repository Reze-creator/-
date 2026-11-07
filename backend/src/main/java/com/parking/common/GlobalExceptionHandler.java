package com.parking.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
    return ResponseEntity.status(e.status()).body(ApiResponse.error(e.code(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .sorted()
            .findFirst()
            .orElse("参数不合法");
    return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", message));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ApiResponse<Void>> malformed(Exception e) {
    return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "参数、枚举或JSON格式不正确"));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException e) {
    return ResponseEntity.status(409).body(ApiResponse.error("CONFLICT", "记录已存在或并发提交冲突"));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ApiResponse<Void>> dependency(DataAccessException e) {
    LOG.error("Database failure: {}", e.getClass().getSimpleName());
    return ResponseEntity.status(503)
        .body(ApiResponse.error("DEPENDENCY_UNAVAILABLE", "数据库暂时不可用，请稍后重试"));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> missing(Exception e) {
    return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "接口或页面不存在"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> method(Exception e) {
    return ResponseEntity.status(405).body(ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不支持"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
    LOG.error("Unhandled server failure", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
  }
}
