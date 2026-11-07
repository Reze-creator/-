package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BookingRules;
import com.parking.common.BusinessException;
import com.parking.common.Hashing;
import com.parking.common.Pages;
import com.parking.common.Types.BookingStatus;
import com.parking.common.Types.ResourceType;
import com.parking.common.Types.Role;
import com.parking.dto.CreateBookingRequest;
import com.parking.entity.BookingEntity;
import com.parking.entity.PaymentEntity;
import com.parking.entity.ResourceEntity;
import com.parking.mapper.BookingMapper;
import com.parking.mapper.PaymentMapper;
import com.parking.mapper.ResourceMapper;
import com.parking.service.BookingService;
import com.parking.vo.BookingView;
import com.parking.vo.UserView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingServiceImpl implements BookingService {
  private final BookingMapper bookings;
  private final ResourceMapper resources;
  private final PaymentMapper payments;
  private final Clock clock;
  private final int holdMinutes;
  private final TransactionTemplate creationTransaction;

  public BookingServiceImpl(
      BookingMapper bookings,
      ResourceMapper resources,
      PaymentMapper payments,
      Clock clock,
      PlatformTransactionManager transactionManager,
      @Value("${parking.hold-minutes}") int holdMinutes) {
    this.bookings = bookings;
    this.resources = resources;
    this.payments = payments;
    this.clock = clock;
    this.holdMinutes = holdMinutes;
    creationTransaction = new TransactionTemplate(transactionManager);
    // 等待资源锁后，必须能看到上一位持锁者刚提交的预约，避免可重复读旧快照。
    creationTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    creationTransaction.setTimeout(15);
  }

  @Override
  public BookingView create(UserView user, CreateBookingRequest input) {
    CreateBookingRequest request = normalize(input);
    String fingerprint = fingerprint(request);
    BookingEntity previous = findByRequest(user.id(), request.requestId());
    if (previous != null) return replay(previous, fingerprint);
    try {
      return creationTransaction.execute(
          status -> createUnderResourceLock(user, request, fingerprint));
    } catch (DuplicateKeyException exception) {
      // 必须等自己的失败事务彻底回滚，再读取并发请求中胜者已提交的记录。
      BookingEntity winner = findByRequest(user.id(), request.requestId());
      if (winner != null) return replay(winner, fingerprint);
      throw exception;
    }
  }

  private BookingView createUnderResourceLock(
      UserView user, CreateBookingRequest request, String fingerprint) {
    ResourceEntity resource = resources.lock(request.resourceId());
    if (resource == null) throw BusinessException.missing("资源不存在");
    // 两个相同请求可能同时通过事务外查询；获得资源锁后再次检查业务幂等。
    BookingEntity previous = findByRequest(user.id(), request.requestId());
    if (previous != null) return replay(previous, fingerprint);
    Instant now = clock.instant();
    BookingRules.validateWindow(request.startAt(), request.endAt(), now);
    if (!resource.getEnabled()) throw BusinessException.conflict("该资源已停用，请选择其他车位");
    ensureNoOverlap(resource.getId(), request.startAt(), request.endAt(), now);
    BookingEntity booking = buildBooking(user, request, resource, fingerprint, now);
    bookings.insert(booking);
    return toView(booking);
  }

  private void ensureNoOverlap(String resourceId, Instant start, Instant end, Instant now) {
    if (!bookings.findBusyResourceIds(resourceId, start, end, now).isEmpty()) {
      throw new BusinessException(HttpStatus.CONFLICT, "RESOURCE_UNAVAILABLE", "所选时段已被预约，请更换车位或时间");
    }
  }

  private BookingEntity buildBooking(
      UserView user,
      CreateBookingRequest request,
      ResourceEntity resource,
      String fingerprint,
      Instant now) {
    BookingEntity booking = new BookingEntity();
    booking.setId(UUID.randomUUID().toString());
    booking.setBookingNo(
        "PK" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
    booking.setUserId(user.id());
    booking.setRequestId(request.requestId());
    booking.setFingerprint(fingerprint);
    booking.setResourceId(resource.getId());
    booking.setResourceCode(resource.getCode());
    booking.setParkName(resource.getParkName());
    booking.setResourceType(resource.getResourceType());
    booking.setPlateNumber(request.plateNumber());
    booking.setStartAt(request.startAt());
    booking.setEndAt(request.endAt());
    booking.setHourlyRate(resource.getHourlyRate());
    booking.setAmount(
        calculateAmount(resource.getHourlyRate(), request.startAt(), request.endAt()));
    booking.setStatus(BookingStatus.PENDING_PAYMENT.name());
    Instant deadline = now.plus(Duration.ofMinutes(holdMinutes));
    booking.setHoldUntil(deadline.isBefore(request.startAt()) ? deadline : request.startAt());
    booking.setCreatedAt(now);
    booking.setUpdatedAt(now);
    return booking;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BookingView pay(UserView user, String id) {
    BookingEntity booking = requireOwnedForUpdate(user, id);
    if (expireIfNeeded(booking)) return toView(booking);
    if (java.util.Set.of("CONFIRMED", "IN_USE", "COMPLETED").contains(booking.getStatus()))
      return toView(booking);
    if (!BookingStatus.PENDING_PAYMENT.name().equals(booking.getStatus()))
      throw BusinessException.conflict("当前预约状态不支持支付");
    Instant now = clock.instant();
    PaymentEntity payment = new PaymentEntity();
    payment.setId(UUID.randomUUID().toString());
    payment.setBookingId(id);
    payment.setAmount(booking.getAmount());
    payment.setStatus("SUCCESS");
    payment.setPaidAt(now);
    payments.insert(payment);
    booking.setStatus(BookingStatus.CONFIRMED.name());
    booking.setPaidAt(now);
    booking.setUpdatedAt(now);
    bookings.updateById(booking);
    return toView(booking);
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BookingView cancel(UserView user, String id) {
    BookingEntity booking = requireOwnedForUpdate(user, id);
    if (expireIfNeeded(booking) || BookingStatus.CANCELLED.name().equals(booking.getStatus()))
      return toView(booking);
    if (java.util.Set.of("IN_USE", "COMPLETED").contains(booking.getStatus()))
      throw BusinessException.conflict("已入场或已完成的预约不能取消，请查看会话与账单");
    Instant now = clock.instant();
    if (!booking.getStartAt().isAfter(now)) throw BusinessException.conflict("预约已开始，不支持取消");
    if (BookingStatus.CONFIRMED.name().equals(booking.getStatus())) refundPayment(id, now);
    booking.setStatus(BookingStatus.CANCELLED.name());
    booking.setCancelledAt(now);
    booking.setUpdatedAt(now);
    bookings.updateById(booking);
    return toView(booking);
  }

  private void refundPayment(String bookingId, Instant now) {
    PaymentEntity payment =
        payments.selectOne(new QueryWrapper<PaymentEntity>().eq("booking_id", bookingId));
    if (payment == null) throw new IllegalStateException("Confirmed booking has no payment");
    payment.setStatus("REFUNDED");
    payment.setRefundedAt(now);
    payments.updateById(payment);
  }

  private boolean expireIfNeeded(BookingEntity booking) {
    if (BookingStatus.EXPIRED.name().equals(booking.getStatus())) return true;
    if (BookingStatus.PENDING_PAYMENT.name().equals(booking.getStatus())
        && !booking.getHoldUntil().isAfter(clock.instant())) {
      booking.setStatus(BookingStatus.EXPIRED.name());
      booking.setUpdatedAt(clock.instant());
      bookings.updateById(booking);
      return true;
    }
    return false;
  }

  @Override
  public BookingView get(UserView user, String id) {
    BookingEntity booking = bookings.selectById(id);
    if (booking == null || (!booking.getUserId().equals(user.id()) && user.role() != Role.ADMIN))
      throw BusinessException.missing("预约不存在");
    return toView(booking);
  }

  @Override
  public Pages<BookingView> list(
      UserView user, BookingStatus status, int page, int size, boolean allUsers) {
    if (allUsers && user.role() != Role.ADMIN) throw BusinessException.forbidden();
    // Also refresh on reads, so filtering by EXPIRED never depends on scheduler timing.
    bookings.expireDue(clock.instant());
    QueryWrapper<BookingEntity> query =
        new QueryWrapper<BookingEntity>()
            .eq(!allUsers, "user_id", user.id())
            .eq(status != null, "status", status == null ? null : status.name());
    return Pages.select(bookings, query, page, size, this::toView);
  }

  private BookingEntity requireOwnedForUpdate(UserView user, String id) {
    BookingEntity snapshot = bookings.selectById(id);
    if (snapshot == null || !snapshot.getUserId().equals(user.id()))
      throw BusinessException.missing("预约不存在");
    // 所有改变占用的路径统一先锁资源、再锁预约。支付跨越保留截止时，新建也必须等待。
    resources.lock(snapshot.getResourceId());
    BookingEntity booking = bookings.lock(id);
    // Administrators can inspect all bookings, but cannot pay/cancel someone else's booking.
    if (booking == null || !booking.getUserId().equals(user.id()))
      throw BusinessException.missing("预约不存在");
    return booking;
  }

  private BookingEntity findByRequest(String userId, String requestId) {
    return bookings.selectOne(
        new QueryWrapper<BookingEntity>().eq("user_id", userId).eq("request_id", requestId));
  }

  private BookingView replay(BookingEntity booking, String fingerprint) {
    if (!booking.getFingerprint().equals(fingerprint))
      throw new BusinessException(
          HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "同一requestId不能修改预约参数");
    return toView(booking);
  }

  private BookingView toView(BookingEntity booking) {
    BookingStatus status = BookingStatus.valueOf(booking.getStatus());
    Instant now = clock.instant();
    if (status == BookingStatus.PENDING_PAYMENT && !booking.getHoldUntil().isAfter(now))
      status = BookingStatus.EXPIRED;
    PaymentEntity payment =
        payments.selectOne(new QueryWrapper<PaymentEntity>().eq("booking_id", booking.getId()));
    boolean canCancel =
        booking.getStartAt().isAfter(now)
            && (status == BookingStatus.PENDING_PAYMENT || status == BookingStatus.CONFIRMED);
    return new BookingView(
        booking.getId(),
        booking.getBookingNo(),
        booking.getResourceId(),
        booking.getResourceCode(),
        booking.getParkName(),
        ResourceType.valueOf(booking.getResourceType()),
        booking.getPlateNumber(),
        booking.getStartAt(),
        booking.getEndAt(),
        booking.getHourlyRate(),
        booking.getAmount(),
        status,
        booking.getHoldUntil(),
        booking.getPaidAt(),
        booking.getCancelledAt(),
        booking.getCreatedAt(),
        payment == null ? "UNPAID" : payment.getStatus(),
        canCancel);
  }

  private static CreateBookingRequest normalize(CreateBookingRequest request) {
    return new CreateBookingRequest(
        request.requestId(),
        request.resourceId().toLowerCase(Locale.ROOT),
        request.plateNumber().toUpperCase(Locale.ROOT),
        request.startAt(),
        request.endAt());
  }

  private static String fingerprint(CreateBookingRequest request) {
    return Hashing.sha256(
        String.join(
            "|",
            request.resourceId(),
            request.plateNumber(),
            request.startAt().toString(),
            request.endAt().toString()));
  }

  private static BigDecimal calculateAmount(BigDecimal hourlyRate, Instant start, Instant end) {
    long halfHours = Duration.between(start, end).toMinutes() / 30;
    return hourlyRate
        .multiply(BigDecimal.valueOf(halfHours))
        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
  }
}
