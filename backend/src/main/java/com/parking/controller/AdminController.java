package com.parking.controller;

import com.parking.common.AdminOnly;
import com.parking.common.ApiResponse;
import com.parking.common.Pages;
import com.parking.common.Types.BookingStatus;
import com.parking.common.UserContext;
import com.parking.dto.ResourceStatusRequest;
import com.parking.service.BookingService;
import com.parking.service.ResourceService;
import com.parking.vo.BookingView;
import com.parking.vo.ResourceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AdminOnly
@RequestMapping("/api/admin")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "04 运营管理")
public class AdminController {
  private final ResourceService resources;
  private final BookingService bookings;

  public AdminController(ResourceService resources, BookingService bookings) {
    this.resources = resources;
    this.bookings = bookings;
  }

  @GetMapping("/resources")
  @Operation(summary = "查看包括停用资源的完整列表")
  public ApiResponse<List<ResourceView>> resources() {
    return ApiResponse.ok(resources.list(null, null, null, null, true));
  }

  @PatchMapping("/resources/{id}/status")
  @Operation(summary = "启停资源，不改变已有订单")
  public ApiResponse<ResourceView> status(
      HttpServletRequest http,
      @PathVariable String id,
      @Valid @RequestBody ResourceStatusRequest request) {
    return ApiResponse.ok(resources.setEnabled(UserContext.current(http), id, request.enabled()));
  }

  @GetMapping("/bookings")
  @Operation(summary = "分页查看全平台预约")
  public ApiResponse<Pages<BookingView>> bookings(
      HttpServletRequest http,
      @RequestParam(required = false) BookingStatus status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(bookings.list(UserContext.current(http), status, page, size, true));
  }
}
