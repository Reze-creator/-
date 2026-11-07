package com.parking.service;

import com.parking.entity.BookingEntity;
import com.parking.entity.UsageSessionEntity;

/** 内部结算能力；由持有资源锁和预约锁的会话事务调用。 */
public interface SettlementService {
  void settle(BookingEntity booking, UsageSessionEntity session);
}
