package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BusinessException;
import com.parking.common.Hashing;
import com.parking.common.Pages;
import com.parking.common.SessionRules;
import com.parking.common.Types.DeviceEventType;
import com.parking.common.Types.EventAction;
import com.parking.common.Types.Role;
import com.parking.dto.DeviceEventRequest;
import com.parking.dto.EventActionRequest;
import com.parking.entity.BookingEntity;
import com.parking.entity.DeviceEventEntity;
import com.parking.entity.UsageBillEntity;
import com.parking.entity.UsageSessionEntity;
import com.parking.mapper.BookingMapper;
import com.parking.mapper.DeviceEventMapper;
import com.parking.mapper.ResourceMapper;
import com.parking.mapper.UsageBillMapper;
import com.parking.mapper.UsageSessionMapper;
import com.parking.service.OperationsService;
import com.parking.service.OutboxService;
import com.parking.service.SettlementService;
import com.parking.service.UsageService;
import com.parking.vo.BillView;
import com.parking.vo.EventView;
import com.parking.vo.UsageView;
import com.parking.vo.UserView;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 事件收件箱、连续序号处理、会话查询。真正的设备接入可复用 receive 的业务入口。 */
@Service
public class UsageServiceImpl implements UsageService {
  private final BookingMapper bookings;
  private final ResourceMapper resources;
  private final DeviceEventMapper events;
  private final UsageSessionMapper sessions;
  private final UsageBillMapper bills;
  private final OperationsService operations;
  private final SettlementService settlement;
  private final Clock clock;
  private final BigDecimal energyRate;
  private final TransactionTemplate transaction;
  private final OutboxService outbox;

  public UsageServiceImpl(
      BookingMapper bookings,
      ResourceMapper resources,
      DeviceEventMapper events,
      UsageSessionMapper sessions,
      UsageBillMapper bills,
      OperationsService operations,
      SettlementService settlement,
      Clock clock,
      OutboxService outbox,
      PlatformTransactionManager manager,
      @Value("${parking.energy-rate:1.20}") BigDecimal energyRate) {
    this.bookings = bookings;
    this.resources = resources;
    this.events = events;
    this.sessions = sessions;
    this.bills = bills;
    this.operations = operations;
    this.settlement = settlement;
    this.clock = clock;
    this.outbox = outbox;
    this.energyRate = energyRate;
    if (energyRate.signum() < 0 || energyRate.scale() > 2)
      throw new IllegalArgumentException("Invalid energy rate");
    transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transaction.setTimeout(15);
  }

  @Override
  public EventView receive(UserView actor, DeviceEventRequest request) {
    requireAdmin(actor);
    String fingerprint =
        Hashing.sha256(
            String.join(
                "|",
                request.bookingId(),
                "" + request.sequenceNo(),
                request.type().name(),
                request.occurredAt().toString(),
                request.energyKwh().stripTrailingZeros().toPlainString()));
    try {
      return transaction.execute(status -> receiveLocked(actor, request, fingerprint));
    } catch (DuplicateKeyException collision) {
      // 跨资源重用 eventId 也由主键兜底；失败事务回滚后才能查询胜者。
      DeviceEventEntity winner = events.selectById(request.eventId());
      if (winner != null) return replay(winner, fingerprint);
      throw collision;
    }
  }

  private EventView receiveLocked(UserView actor, DeviceEventRequest request, String fingerprint) {
    BookingEntity booking = lockBooking(request.bookingId());
    DeviceEventEntity previous = events.selectById(request.eventId());
    if (previous != null) return replay(previous, fingerprint);
    DeviceEventEntity sequenceOwner = eventAt(booking.getId(), request.sequenceNo());
    if (sequenceOwner != null) throw BusinessException.conflict("该预约序号已属于其他 eventId，请重发原事件或处理异常");
    int cursor = booking.getLastEventSequence();
    if (request.sequenceNo() <= cursor || request.sequenceNo() > cursor + 100)
      throw BusinessException.invalid("事件序号必须位于下一个序号至其后 99 个序号范围内");
    DeviceEventEntity event = new DeviceEventEntity();
    event.setId(request.eventId());
    event.setBookingId(booking.getId());
    event.setResourceId(booking.getResourceId());
    event.setSequenceNo(request.sequenceNo());
    event.setFingerprint(fingerprint);
    event.setEventType(request.type().name());
    event.setOccurredAt(request.occurredAt());
    event.setEnergyKwh(request.energyKwh());
    event.setStatus("WAITING");
    event.setCreatedAt(clock.instant());
    events.insert(event);
    if (outbox.enabled()) {
      // 事件和待发送消息一起落库；MQ 暂停时仍可接收，待恢复后由消费者处理。
      outbox.enqueue("DEVICE_EVENT", event.getId());
    } else drain(booking);
    operations.audit(
        actor,
        "RECEIVE_DEVICE_EVENT",
        request.eventId(),
        "模拟事件 " + request.type() + "，序号 " + request.sequenceNo());
    return eventView(events.selectById(event.getId()));
  }

  private BookingEntity lockBooking(String id) {
    BookingEntity snapshot = bookings.selectById(id);
    if (snapshot == null) throw BusinessException.missing("预约不存在");
    // 与预约创建、支付和取消一致：资源锁在前，预约锁在后。
    resources.lock(snapshot.getResourceId());
    return bookings.lock(id);
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void processPendingEvent(String eventId) {
    DeviceEventEntity event = events.selectById(eventId);
    if (event == null) throw new IllegalStateException("Device event missing");
    BookingEntity booking = lockBooking(event.getBookingId());
    drain(booking);
  }

  private EventView replay(DeviceEventEntity event, String fingerprint) {
    if (!event.getFingerprint().equals(fingerprint))
      throw BusinessException.conflict("同一 eventId 不能修改事件内容");
    return eventView(event);
  }

  private DeviceEventEntity eventAt(String bookingId, int sequence) {
    return events.selectOne(
        new QueryWrapper<DeviceEventEntity>()
            .eq("booking_id", bookingId)
            .eq("sequence_no", sequence));
  }

  /** 只取下一个连续序号；收到第 3 条时不会越过缺失的第 2 条。 */
  private void drain(BookingEntity booking) {
    for (int count = 0; count < 100; count++) {
      DeviceEventEntity event = eventAt(booking.getId(), booking.getLastEventSequence() + 1);
      if (event == null || !"WAITING".equals(event.getStatus())) return;
      UsageSessionEntity session =
          sessions.selectOne(
              new QueryWrapper<UsageSessionEntity>().eq("booking_id", booking.getId()));
      try {
        SessionRules.validate(booking, session, event);
        if ("ENTER".equals(event.getEventType())
            && sessions.selectCount(
                    new QueryWrapper<UsageSessionEntity>()
                        .eq("resource_id", booking.getResourceId())
                        .ne("status", "FINISHED"))
                > 0) throw BusinessException.conflict("资源上还有未离场会话，请先处理运营异常");
      } catch (BusinessException failure) {
        // 业务错误作为持久化异常返回；HTTP 成功只表示事件已经接收，不表示应用成功。
        event.setStatus("FAILED");
        event.setErrorMessage(failure.getMessage());
        events.updateById(event);
        operations.report(
            "EVENT_FAILURE",
            event.getId(),
            booking.getResourceId(),
            booking.getId(),
            "设备事件处理失败",
            failure.getMessage());
        return;
      }
      apply(booking, session, event);
      event.setStatus("APPLIED");
      event.setErrorMessage("");
      event.setProcessedAt(clock.instant());
      events.updateById(event);
      booking.setLastEventSequence(event.getSequenceNo());
      booking.setUpdatedAt(clock.instant());
      bookings.updateById(booking);
    }
  }

  private void apply(BookingEntity booking, UsageSessionEntity session, DeviceEventEntity event) {
    DeviceEventType type = DeviceEventType.valueOf(event.getEventType());
    if (type == DeviceEventType.ENTER) {
      session = new UsageSessionEntity();
      session.setId(UUID.randomUUID().toString());
      session.setBookingId(booking.getId());
      session.setResourceId(booking.getResourceId());
      session.setUserId(booking.getUserId());
      session.setStatus("PARKED");
      session.setEnteredAt(event.getOccurredAt());
      session.setLastEventAt(event.getOccurredAt());
      session.setEnergyKwh(BigDecimal.ZERO);
      session.setEnergyRate(energyRate);
      session.setCreatedAt(clock.instant());
      session.setUpdatedAt(clock.instant());
      sessions.insert(session);
      booking.setStatus("IN_USE");
      return;
    }
    switch (type) {
      case CHARGE_STARTED -> {
        session.setStatus("CHARGING");
        session.setChargingStartedAt(event.getOccurredAt());
      }
      case CHARGE_STOPPED -> {
        session.setStatus("PARKED");
        session.setChargingStoppedAt(event.getOccurredAt());
      }
      case EXIT -> {
        session.setStatus("FINISHED");
        session.setExitedAt(event.getOccurredAt());
      }
      default -> {
        /* METER_REPORTED 仅更新累计表数。 */
      }
    }
    session.setEnergyKwh(event.getEnergyKwh());
    session.setLastEventAt(event.getOccurredAt());
    session.setUpdatedAt(clock.instant());
    sessions.updateById(session);
    if (type == DeviceEventType.EXIT) {
      // 账单、预付款状态、会话及事件游标一起提交，任一数据库错误全部回滚。
      settlement.settle(booking, session);
      booking.setStatus("COMPLETED");
    }
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public EventView handleEvent(UserView actor, String id, EventActionRequest request) {
    requireAdmin(actor);
    DeviceEventEntity snapshot = events.selectById(id);
    if (snapshot == null) throw BusinessException.missing("事件不存在");
    BookingEntity booking = lockBooking(snapshot.getBookingId());
    DeviceEventEntity event = events.selectById(id);
    if (Set.of("APPLIED", "SKIPPED").contains(event.getStatus())) return eventView(event);
    if (request.action() == EventAction.SKIP) {
      if (event.getSequenceNo() != booking.getLastEventSequence() + 1)
        throw BusinessException.conflict("只能跳过当前阻塞的下一个序号，不能跨过缺失事件");
      event.setStatus("SKIPPED");
      event.setErrorMessage(request.reason().trim());
      event.setProcessedAt(clock.instant());
      events.updateById(event);
      booking.setLastEventSequence(event.getSequenceNo());
      bookings.updateById(booking);
    } else {
      event.setStatus("WAITING");
      event.setErrorMessage("");
      events.updateById(event);
    }
    drain(booking);
    operations.audit(
        actor,
        request.action() == EventAction.SKIP ? "SKIP_EVENT" : "RETRY_EVENT",
        id,
        request.reason().trim());
    return eventView(events.selectById(id));
  }

  @Override
  public Pages<EventView> events(String bookingId, int page, int size) {
    return Pages.select(
        events,
        new QueryWrapper<DeviceEventEntity>().eq(bookingId != null, "booking_id", bookingId),
        page,
        size,
        this::eventView);
  }

  @Override
  public Pages<UsageView> sessions(UserView user, boolean all, int page, int size) {
    if (all) requireAdmin(user);
    return Pages.select(
        sessions,
        new QueryWrapper<UsageSessionEntity>().eq(!all, "user_id", user.id()),
        page,
        size,
        s -> {
          BookingEntity b = bookings.selectById(s.getBookingId());
          return new UsageView(
              s.getId(),
              s.getBookingId(),
              b.getResourceCode(),
              b.getParkName(),
              b.getResourceType(),
              b.getPlateNumber(),
              s.getStatus(),
              s.getEnteredAt(),
              s.getChargingStartedAt(),
              s.getChargingStoppedAt(),
              s.getExitedAt(),
              s.getEnergyKwh(),
              s.getEnergyRate(),
              b.getLastEventSequence());
        });
  }

  @Override
  public Pages<BillView> bills(UserView user, boolean all, int page, int size) {
    if (all) requireAdmin(user);
    return Pages.select(
        bills,
        new QueryWrapper<UsageBillEntity>().eq(!all, "user_id", user.id()),
        page,
        size,
        b -> {
          BookingEntity booking = bookings.selectById(b.getBookingId());
          return new BillView(
              b.getId(),
              b.getBookingId(),
              b.getSessionId(),
              booking.getResourceCode(),
              b.getParkingMinutes(),
              b.getParkingAmount(),
              b.getEnergyKwh(),
              b.getEnergyRate(),
              b.getEnergyAmount(),
              b.getTotalAmount(),
              b.getPrepaidAmount(),
              b.getAdditionalAmount(),
              b.getRefundAmount(),
              b.getStatus(),
              b.getCreatedAt());
        });
  }

  private EventView eventView(DeviceEventEntity event) {
    return new EventView(
        event.getId(),
        event.getBookingId(),
        event.getSequenceNo(),
        event.getEventType(),
        event.getOccurredAt(),
        event.getEnergyKwh(),
        event.getStatus(),
        event.getErrorMessage(),
        event.getCreatedAt());
  }

  private static void requireAdmin(UserView actor) {
    if (actor.role() != Role.ADMIN) throw BusinessException.forbidden();
  }
}
