package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.PublicEndpoint;
import com.parking.common.Types.ResourceType;
import com.parking.service.ResourceService;
import com.parking.vo.ParkView;
import com.parking.vo.ResourceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PublicEndpoint
@RequestMapping("/api")
@Tag(name = "02 园区与资源")
public class ResourceController {
  private final ResourceService resources;

  public ResourceController(ResourceService resources) {
    this.resources = resources;
  }

  @GetMapping("/parks")
  @Operation(summary = "园区列表")
  public ApiResponse<List<ParkView>> parks() {
    return ApiResponse.ok(resources.parks());
  }

  @GetMapping("/resources")
  @Operation(summary = "查询资源及指定时段可用性")
  public ApiResponse<List<ResourceView>> list(
      @RequestParam(required = false) String parkCode,
      @RequestParam(required = false) ResourceType type,
      @RequestParam(required = false) Instant startAt,
      @RequestParam(required = false) Instant endAt) {
    return ApiResponse.ok(resources.list(parkCode, type, startAt, endAt, false));
  }

  @GetMapping("/resources/{id}")
  @Operation(summary = "资源详情")
  public ApiResponse<ResourceView> get(@PathVariable String id) {
    return ApiResponse.ok(resources.get(id));
  }
}
