package com.parking.controller;

import com.parking.common.AdminOnly;
import com.parking.common.ApiResponse;
import com.parking.common.Pages;
import com.parking.common.UserContext;
import com.parking.dto.ResolveIncidentRequest;
import com.parking.service.OperationsService;
import com.parking.vo.AuditView;
import com.parking.vo.IncidentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AdminOnly
@RequestMapping("/api/admin/operations")
@SecurityRequirement(name = "BearerAuth")
public class OperationsController {
  private final OperationsService operations;

  public OperationsController(OperationsService operations) {
    this.operations = operations;
  }

  @GetMapping("/overview")
  @Operation(summary = "业务异常计数与当前进程 HTTP 指标")
  public ApiResponse<Map<String, Object>> overview() {
    return ApiResponse.ok(operations.overview());
  }

  @PostMapping("/scan")
  @Operation(summary = "立即扫描心跳超时 事件缺号和超时会话")
  public ApiResponse<Void> scan(HttpServletRequest http) {
    operations.scan();
    operations.audit(UserContext.current(http), "SCAN_INCIDENTS", "platform", "手动检查运营异常");
    return ApiResponse.ok(null);
  }

  @GetMapping("/incidents")
  @Operation(summary = "异常工单分页")
  public ApiResponse<Pages<IncidentView>> incidents(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(operations.incidents(status, page, size));
  }

  @PostMapping("/incidents/{id}/resolve")
  @Operation(summary = "业务条件恢复后关闭工单，必须填写处理说明")
  public ApiResponse<IncidentView> resolve(
      HttpServletRequest http,
      @PathVariable String id,
      @Valid @RequestBody ResolveIncidentRequest r) {
    return ApiResponse.ok(operations.resolve(UserContext.current(http), id, r.resolution()));
  }

  @GetMapping("/audits")
  @Operation(summary = "分页查询管理操作审计和请求跟踪标识")
  public ApiResponse<Pages<AuditView>> audits(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(operations.audits(page, size));
  }
}
