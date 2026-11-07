package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.Pages;
import com.parking.common.UserContext;
import com.parking.service.UsageService;
import com.parking.vo.BillView;
import com.parking.vo.UsageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "BearerAuth")
public class UsageController {
  private final UsageService usage;

  public UsageController(UsageService usage) {
    this.usage = usage;
  }

  @GetMapping("/sessions")
  @Operation(summary = "分页查看本人停车充电会话")
  public ApiResponse<Pages<UsageView>> sessions(
      HttpServletRequest http,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(usage.sessions(UserContext.current(http), false, page, size));
  }

  @GetMapping("/bills")
  @Operation(summary = "分页查看本人实际费用与模拟补退差额")
  public ApiResponse<Pages<BillView>> bills(
      HttpServletRequest http,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(usage.bills(UserContext.current(http), false, page, size));
  }
}
