package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BookingRules;
import com.parking.common.BusinessException;
import com.parking.common.Types.ResourceType;
import com.parking.common.Types.Role;
import com.parking.entity.ResourceEntity;
import com.parking.mapper.BookingMapper;
import com.parking.mapper.ResourceMapper;
import com.parking.service.OperationsService;
import com.parking.service.ResourceService;
import com.parking.vo.ParkView;
import com.parking.vo.ResourceView;
import com.parking.vo.UserView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceServiceImpl implements ResourceService {
  private final ResourceMapper resources;
  private final BookingMapper bookings;
  private final Clock clock;
  private final OperationsService operations;

  public ResourceServiceImpl(
      ResourceMapper resources, BookingMapper bookings, Clock clock, OperationsService operations) {
    this.resources = resources;
    this.bookings = bookings;
    this.clock = clock;
    this.operations = operations;
  }

  @Override
  public List<ParkView> parks() {
    Map<String, List<ResourceEntity>> grouped = new LinkedHashMap<>();
    for (ResourceEntity e :
        resources.selectList(new QueryWrapper<ResourceEntity>().orderByAsc("park_code", "code")))
      grouped.computeIfAbsent(e.getParkCode(), k -> new ArrayList<>()).add(e);
    return grouped.values().stream()
        .map(
            items -> {
              ResourceEntity e = items.get(0);
              return new ParkView(e.getParkCode(), e.getParkName(), e.getLocation(), items.size());
            })
        .toList();
  }

  @Override
  public List<ResourceView> list(
      String parkCode, ResourceType type, Instant startAt, Instant endAt, boolean includeDisabled) {
    if ((startAt == null) != (endAt == null)) throw BusinessException.invalid("startAt与endAt须同时提供");
    Set<String> busy = new HashSet<>();
    if (startAt != null) {
      BookingRules.validateWindow(startAt, endAt, clock.instant());
      busy.addAll(bookings.findBusyResourceIds(null, startAt, endAt, clock.instant()));
    }
    QueryWrapper<ResourceEntity> q =
        new QueryWrapper<ResourceEntity>()
            .eq(parkCode != null && !parkCode.isBlank(), "park_code", parkCode)
            .eq(type != null, "resource_type", type == null ? null : type.name())
            .eq(!includeDisabled, "enabled", true)
            .orderByAsc("park_code", "code");
    boolean checked = startAt != null;
    return resources.selectList(q).stream()
        .map(e -> view(e, checked ? e.getEnabled() && !busy.contains(e.getId()) : null))
        .toList();
  }

  @Override
  public ResourceView get(String id) {
    ResourceEntity e = resources.selectById(id);
    if (e == null) throw BusinessException.missing("资源不存在");
    return view(e, null);
  }

  @Override
  @Transactional
  public ResourceView setEnabled(UserView actor, String id, boolean enabled) {
    if (actor.role() != Role.ADMIN) throw BusinessException.forbidden();
    ResourceEntity e = resources.lock(id);
    if (e == null) throw BusinessException.missing("资源不存在");
    e.setEnabled(enabled);
    resources.updateById(e);
    operations.audit(actor, "SET_RESOURCE_STATUS", id, enabled ? "启用资源" : "停用资源，已有预约不变");
    return view(e, null);
  }

  private static ResourceView view(ResourceEntity e, Boolean available) {
    return new ResourceView(
        e.getId(),
        e.getCode(),
        e.getParkCode(),
        e.getParkName(),
        e.getLocation(),
        ResourceType.valueOf(e.getResourceType()),
        e.getPowerKw(),
        e.getHourlyRate(),
        e.getEnabled(),
        available);
  }
}
