package com.parking.service;

import com.parking.common.Types.ResourceType;
import com.parking.vo.ParkView;
import com.parking.vo.ResourceView;
import com.parking.vo.UserView;
import java.time.Instant;
import java.util.List;

public interface ResourceService {
  List<ParkView> parks();

  List<ResourceView> list(
      String parkCode, ResourceType type, Instant startAt, Instant endAt, boolean includeDisabled);

  ResourceView get(String id);

  ResourceView setEnabled(UserView actor, String id, boolean enabled);
}
