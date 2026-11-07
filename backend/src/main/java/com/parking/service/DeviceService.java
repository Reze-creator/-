package com.parking.service;

import com.parking.dto.HeartbeatRequest;
import com.parking.vo.DeviceView;
import com.parking.vo.UserView;
import java.util.List;

public interface DeviceService {
  List<DeviceView> list();

  DeviceView heartbeat(UserView actor, String resourceId, HeartbeatRequest request);
}
