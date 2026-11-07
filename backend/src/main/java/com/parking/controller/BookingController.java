package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.Pages;
import com.parking.common.Types.BookingStatus;
import com.parking.common.UserContext;
import com.parking.dto.CreateBookingRequest;
import com.parking.service.BookingService;
import com.parking.vo.BookingView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "03 我的预约")
public class BookingController {
  private final BookingService bookings;

  public BookingController(BookingService bookings) {
    this.bookings = bookings;
  }

  @PostMapping
  @Operation(summary = "预占时段并创建待支付预约，同一requestId幂等")
  public ApiResponse<BookingView> create(
      HttpServletRequest http, @Valid @RequestBody CreateBookingRequest request) {
    return ApiResponse.ok(bookings.create(UserContext.current(http), request));
  }

  @GetMapping
  @Operation(summary = "分页查询本人预约")
  public ApiResponse<Pages<BookingView>> list(
      HttpServletRequest http,
      @RequestParam(required = false) BookingStatus status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(bookings.list(UserContext.current(http), status, page, size, false));
  }

  @GetMapping("/{id}")
  @Operation(summary = "预约详情，管理员可查看全量")
  public ApiResponse<BookingView> get(HttpServletRequest http, @PathVariable String id) {
    return ApiResponse.ok(bookings.get(UserContext.current(http), id));
  }

  @PostMapping("/{id}/pay")
  @Operation(summary = "模拟支付，到期则返回EXPIRED且不记账")
  public ApiResponse<BookingView> pay(HttpServletRequest http, @PathVariable String id) {
    return ApiResponse.ok(bookings.pay(UserContext.current(http), id));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "开始前取消；已支付则模拟全额退款")
  public ApiResponse<BookingView> cancel(HttpServletRequest http, @PathVariable String id) {
    return ApiResponse.ok(bookings.cancel(UserContext.current(http), id));
  }
}
