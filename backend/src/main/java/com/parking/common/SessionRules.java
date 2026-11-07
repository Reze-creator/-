package com.parking.common;

import com.parking.common.Types.DeviceEventType;
import com.parking.entity.BookingEntity;
import com.parking.entity.DeviceEventEntity;
import com.parking.entity.UsageSessionEntity;
import java.math.BigDecimal;
import java.time.Duration;

/** 纯业务校验：检查完成后才允许修改实体，失败事件不会留下半更新会话。 */
public final class SessionRules {
  private SessionRules() {}

  public static void validate(
      BookingEntity booking, UsageSessionEntity session, DeviceEventEntity event) {
    DeviceEventType type = DeviceEventType.valueOf(event.getEventType());
    if (event.getOccurredAt().getNano() % 1000000 != 0)
      throw BusinessException.invalid("模拟事件时间精度最多到毫秒");
    if (type == DeviceEventType.ENTER) {
      require(session == null, "此预约已经创建会话");
      require("CONFIRMED".equals(booking.getStatus()), "只有预约成功的订单可以入场");
      require(
          !event.getOccurredAt().isBefore(booking.getStartAt())
              && event.getOccurredAt().isBefore(booking.getEndAt()),
          "入场时间必须位于预约时段内");
      require(event.getEnergyKwh().signum() == 0, "入场时累计电量必须为零");
      return;
    }
    require(session != null, "请先补发 ENTER 入场事件");
    require(
        "IN_USE".equals(booking.getStatus()) && !"FINISHED".equals(session.getStatus()),
        "会话已结束或预约不可履约");
    require(!event.getOccurredAt().isBefore(session.getLastEventAt()), "事件时间不能早于已处理的前序事件");
    require(
        !event.getOccurredAt().isAfter(session.getEnteredAt().plus(Duration.ofHours(48))),
        "模拟会话最长支持 48 小时");
    require(event.getEnergyKwh().compareTo(session.getEnergyKwh()) >= 0, "累计电量不能回退");
    switch (type) {
      case CHARGE_STARTED -> {
        require("CHARGING".equals(booking.getResourceType()), "普通车位不能充电");
        require(
            "PARKED".equals(session.getStatus()) && session.getChargingStartedAt() == null,
            "一次会话只允许启动一次充电");
        require(event.getEnergyKwh().signum() == 0, "开始充电时累计电量必须为零");
      }
      case METER_REPORTED, CHARGE_STOPPED ->
          require("CHARGING".equals(session.getStatus()), "只有充电中的会话可以上报电量或停止充电");
      case EXIT -> {
        require(!"CHARGING".equals(session.getStatus()), "请先补发停止充电事件再离场");
        require(event.getOccurredAt().isAfter(session.getEnteredAt()), "离场必须晚于入场");
        require(event.getEnergyKwh().compareTo(session.getEnergyKwh()) == 0, "离场电量须等于停止充电后的累计电量");
      }
      default -> throw BusinessException.invalid("不支持的事件");
    }
    if ("PARKING".equals(booking.getResourceType()))
      require(event.getEnergyKwh().compareTo(BigDecimal.ZERO) == 0, "普通停车会话电量必须为零");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw BusinessException.conflict(message);
  }
}
