package com.parking.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.entity.SessionEntity;
import com.parking.mapper.BookingMapper;
import com.parking.mapper.SessionMapper;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiryJob {
  private final BookingMapper bookings;
  private final SessionMapper sessions;
  private final Clock clock;

  public ExpiryJob(BookingMapper bookings, SessionMapper sessions, Clock clock) {
    this.bookings = bookings;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${parking.expiry-delay-ms:30000}")
  public void expireBookings() {
    bookings.expireDue(clock.instant());
  }

  @Scheduled(fixedDelay = 3600000)
  public void expireSessions() {
    sessions.delete(new QueryWrapper<SessionEntity>().le("expires_at", clock.instant()));
  }
}
