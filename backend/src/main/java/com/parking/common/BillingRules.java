package com.parking.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/** 实际停车按不足一分钟进一分计费；电费按累计 kWh 计费，金额只在最后舍入到分。 */
public final class BillingRules {
  private BillingRules() {}

  public static long parkingMinutes(Instant enteredAt, Instant exitedAt) {
    long milliseconds = Duration.between(enteredAt, exitedAt).toMillis();
    if (milliseconds <= 0) throw BusinessException.invalid("离场时间必须晚于入场时间");
    return (milliseconds + 59999) / 60000;
  }

  public static BigDecimal parkingAmount(BigDecimal hourlyRate, long minutes) {
    return hourlyRate
        .multiply(BigDecimal.valueOf(minutes))
        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
  }

  public static BigDecimal energyAmount(BigDecimal energy, BigDecimal rate) {
    return energy.multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }
}
