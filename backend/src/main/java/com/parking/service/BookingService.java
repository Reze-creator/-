package com.parking.service;

import com.parking.common.Pages;
import com.parking.common.Types.BookingStatus;
import com.parking.dto.CreateBookingRequest;
import com.parking.vo.BookingView;
import com.parking.vo.UserView;

public interface BookingService {
  BookingView create(UserView user, CreateBookingRequest request);

  BookingView pay(UserView user, String id);

  BookingView cancel(UserView user, String id);

  BookingView get(UserView user, String id);

  Pages<BookingView> list(
      UserView user, BookingStatus status, int page, int size, boolean allUsers);
}
