package com.parking.service;

import com.parking.common.Pages;
import com.parking.dto.DeviceEventRequest;
import com.parking.dto.EventActionRequest;
import com.parking.vo.BillView;
import com.parking.vo.EventView;
import com.parking.vo.UsageView;
import com.parking.vo.UserView;

public interface UsageService {
  void processPendingEvent(String eventId);

  EventView receive(UserView actor, DeviceEventRequest request);

  EventView handleEvent(UserView actor, String eventId, EventActionRequest request);

  Pages<EventView> events(String bookingId, int page, int size);

  Pages<UsageView> sessions(UserView user, boolean all, int page, int size);

  Pages<BillView> bills(UserView user, boolean all, int page, int size);
}
