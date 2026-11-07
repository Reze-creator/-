package com.parking.common;

public final class Types {
  private Types() {}

  public enum Role {
    USER,
    ADMIN
  }

  public enum ResourceType {
    PARKING,
    CHARGING
  }

  public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    IN_USE,
    COMPLETED,
    CANCELLED,
    EXPIRED
  }

  /** 模拟设备事件必须按每个预约的 sequenceNo 连续应用。 */
  public enum DeviceEventType {
    ENTER,
    CHARGE_STARTED,
    METER_REPORTED,
    CHARGE_STOPPED,
    EXIT
  }

  public enum EventAction {
    RETRY,
    SKIP
  }
}
