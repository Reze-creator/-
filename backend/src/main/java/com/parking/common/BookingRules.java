package com.parking.common;

import java.time.Duration;
import java.time.Instant;

public final class BookingRules {
  private BookingRules() {}

  public static void validateWindow(Instant start, Instant end, Instant now) {
    if (start == null || end == null) throw BusinessException.invalid("请选择预约起止时间");
    if (!start.isAfter(now)) throw BusinessException.invalid("预约开始时间必须晚于当前时间");
    if (start.isAfter(now.plus(Duration.ofDays(30)))) throw BusinessException.invalid("最多提前30天预约");
    if (!end.isAfter(start) || Duration.between(start, end).compareTo(Duration.ofHours(24)) > 0)
      throw BusinessException.invalid("预约时长须为30分钟至24小时");
    if (start.getNano() != 0
        || end.getNano() != 0
        || start.getEpochSecond() % 1800 != 0
        || end.getEpochSecond() % 1800 != 0) throw BusinessException.invalid("起止时间须为整点或半点");
  }
}
