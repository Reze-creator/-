package com.parking.config;

import com.parking.service.OperationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationsJob {
  private final OperationsService operations;

  public OperationsJob(OperationsService operations) {
    this.operations = operations;
  }

  /** 只发现异常，不猜测用户何时离场，不替设备制造电表数据。 */
  @Scheduled(
      fixedDelayString = "${parking.operations-delay-ms:30000}",
      initialDelayString = "${parking.operations-delay-ms:30000}")
  public void scan() {
    operations.scan();
  }
}
