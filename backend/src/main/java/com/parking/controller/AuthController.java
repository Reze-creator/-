package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.PublicEndpoint;
import com.parking.common.UserContext;
import com.parking.dto.LoginRequest;
import com.parking.dto.RegisterRequest;
import com.parking.infrastructure.RedisSupport;
import com.parking.service.AuthService;
import com.parking.vo.LoginView;
import com.parking.vo.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "01 用户认证")
public class AuthController {
  private final AuthService auth;
  private final RedisSupport limits;

  public AuthController(AuthService auth, RedisSupport limits) {
    this.auth = auth;
    this.limits = limits;
  }

  @PublicEndpoint
  @PostMapping("/login")
  @Operation(summary = "登录并获取Bearer会话")
  public ApiResponse<LoginView> login(
      HttpServletRequest http, @Valid @RequestBody LoginRequest request) {
    limits.limit("login-ip", http.getRemoteAddr(), 30, 60);
    limits.limit("login-account", request.username().toLowerCase(java.util.Locale.ROOT), 10, 60);
    return ApiResponse.ok(auth.login(request));
  }

  @PublicEndpoint
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "注册普通用户并登录")
  public ApiResponse<LoginView> register(
      HttpServletRequest http, @Valid @RequestBody RegisterRequest request) {
    limits.limit("register-ip", http.getRemoteAddr(), 10, 60);
    return ApiResponse.ok(auth.register(request));
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "BearerAuth")
  @Operation(summary = "当前登录用户")
  public ApiResponse<UserView> me(HttpServletRequest request) {
    return ApiResponse.ok(UserContext.current(request));
  }

  @PostMapping("/logout")
  @SecurityRequirement(name = "BearerAuth")
  @Operation(summary = "注销当前会话")
  public ApiResponse<Void> logout(@RequestHeader("Authorization") String authorization) {
    auth.logout(authorization);
    return ApiResponse.ok(null);
  }
}
