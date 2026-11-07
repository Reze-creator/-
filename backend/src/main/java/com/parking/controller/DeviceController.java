package com.parking.controller;

import com.parking.common.AdminOnly;
import com.parking.common.ApiResponse;
import com.parking.common.Pages;
import com.parking.common.UserContext;
import com.parking.dto.DeviceEventRequest;
import com.parking.dto.EventActionRequest;
import com.parking.dto.HeartbeatRequest;
import com.parking.service.DeviceService;
import com.parking.service.UsageService;
import com.parking.vo.BillView;
import com.parking.vo.DeviceView;
import com.parking.vo.EventView;
import com.parking.vo.UsageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前是管理员控制的模拟入口，不把设备写接口公开给普通车主。 */
@RestController
@AdminOnly
@RequestMapping("/api/admin")
@SecurityRequirement(name = "BearerAuth")
public class DeviceController {
  private final DeviceService devices;
  private final UsageService usage;

  public DeviceController(DeviceService devices, UsageService usage) {
    this.devices = devices;
    this.usage = usage;
  }

  @GetMapping("/devices")
  @Operation(summary = "模拟设备列表及心跳状态")
  public ApiResponse<List<DeviceView>> devices() {
    return ApiResponse.ok(devices.list());
  }

  @PostMapping("/devices/{id}/heartbeat")
  @Operation(summary = "模拟正常或故障心跳")
  public ApiResponse<DeviceView> heartbeat(
      HttpServletRequest http, @PathVariable String id, @Valid @RequestBody HeartbeatRequest r) {
    return ApiResponse.ok(devices.heartbeat(UserContext.current(http), id, r));
  }

  @PostMapping("/device-events")
  @Operation(summary = "接收模拟事件，返回 WAITING APPLIED 或 FAILED，eventId 幂等")
  public ApiResponse<EventView> event(
      HttpServletRequest http, @Valid @RequestBody DeviceEventRequest r) {
    return ApiResponse.ok(usage.receive(UserContext.current(http), r));
  }

  @GetMapping("/device-events")
  @Operation(summary = "分页查看事件处理结果，可按 bookingId 筛选")
  public ApiResponse<Pages<EventView>> events(
      @RequestParam(required = false) String bookingId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(usage.events(bookingId, page, size));
  }

  @PostMapping("/device-events/{id}/action")
  @Operation(summary = "重试事件或有理由地跳过阻塞序号，记录审计")
  public ApiResponse<EventView> action(
      HttpServletRequest http, @PathVariable String id, @Valid @RequestBody EventActionRequest r) {
    return ApiResponse.ok(usage.handleEvent(UserContext.current(http), id, r));
  }

  @GetMapping("/sessions")
  @Operation(summary = "管理员分页查询全平台会话")
  public ApiResponse<Pages<UsageView>> sessions(
      HttpServletRequest http,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(usage.sessions(UserContext.current(http), true, page, size));
  }

  @GetMapping("/bills")
  @Operation(summary = "管理员分页查询全平台实际账单")
  public ApiResponse<Pages<BillView>> bills(
      HttpServletRequest http,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(usage.bills(UserContext.current(http), true, page, size));
  }
}
