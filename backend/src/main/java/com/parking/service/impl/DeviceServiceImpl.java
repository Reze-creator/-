package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BusinessException;
import com.parking.common.Types.Role;
import com.parking.dto.HeartbeatRequest;
import com.parking.entity.DeviceEntity;
import com.parking.entity.ResourceEntity;
import com.parking.mapper.DeviceMapper;
import com.parking.mapper.ResourceMapper;
import com.parking.service.DeviceService;
import com.parking.service.OperationsService;
import com.parking.vo.DeviceView;
import com.parking.vo.UserView;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceServiceImpl implements DeviceService {
  private final ResourceMapper resources;
  private final DeviceMapper devices;
  private final OperationsService operations;
  private final Clock clock;
  private final long offlineSeconds;

  public DeviceServiceImpl(
      ResourceMapper resources,
      DeviceMapper devices,
      OperationsService operations,
      Clock clock,
      @Value("${parking.device-offline-seconds:120}") long offlineSeconds) {
    this.resources = resources;
    this.devices = devices;
    this.operations = operations;
    this.clock = clock;
    this.offlineSeconds = offlineSeconds;
  }

  @Override
  public List<DeviceView> list() {
    return resources.selectList(new QueryWrapper<ResourceEntity>().orderByAsc("code")).stream()
        .map(resource -> view(resource, devices.selectById(resource.getId())))
        .toList();
  }

  @Override
  @Transactional
  public DeviceView heartbeat(UserView actor, String id, HeartbeatRequest request) {
    if (actor.role() != Role.ADMIN) throw BusinessException.forbidden();
    ResourceEntity resource = resources.lock(id);
    if (resource == null) throw BusinessException.missing("资源不存在");
    DeviceEntity device = devices.selectById(id);
    boolean first = device == null;
    if (first) {
      device = new DeviceEntity();
      device.setId(id);
    }
    device.setLastSeenAt(clock.instant());
    device.setUpdatedAt(clock.instant());
    device.setFault(request.fault());
    // 空字符串会写回数据库，确保恢复后不会遗留上次故障描述。
    device.setFaultMessage(
        request.fault() ? (request.message() == null ? "模拟设备故障" : request.message()) : "");
    if (first) devices.insert(device);
    else devices.updateById(device);
    if (request.fault())
      operations.report("DEVICE_FAULT", id, id, null, "设备报告故障", device.getFaultMessage());
    operations.audit(actor, "DEVICE_HEARTBEAT", id, request.fault() ? "模拟故障心跳" : "模拟正常心跳");
    return view(resource, device);
  }

  private DeviceView view(ResourceEntity resource, DeviceEntity device) {
    String state =
        device == null || device.getLastSeenAt() == null
            ? "UNKNOWN"
            : Boolean.TRUE.equals(device.getFault())
                ? "FAULT"
                : !device.getLastSeenAt().isAfter(clock.instant().minusSeconds(offlineSeconds))
                    ? "OFFLINE"
                    : "ONLINE";
    return new DeviceView(
        resource.getId(),
        resource.getCode(),
        resource.getParkName(),
        resource.getResourceType(),
        state,
        device == null ? null : device.getLastSeenAt(),
        device == null ? null : device.getFaultMessage());
  }
}
