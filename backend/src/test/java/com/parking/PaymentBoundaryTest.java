package com.parking;

import static org.assertj.core.api.Assertions.*;

import com.parking.common.BusinessException;
import com.parking.common.Types.Role;
import com.parking.dto.CreateBookingRequest;
import com.parking.service.BookingService;
import com.parking.vo.UserView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 用可控时钟和数据库写入阻塞，复现支付检查在截止前而提交在截止后的竞争。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:boundary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "parking.seed-enabled=false",
      "parking.expiry-delay-ms=600000",
      "parking.operations-delay-ms=600000"
    })
class PaymentBoundaryTest {
  @Autowired BookingService bookings;
  @Autowired JdbcTemplate db;
  @Autowired ControlledClock clock;
  @Autowired InsertGate gate;

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    ControlledClock testClock() {
      return new ControlledClock();
    }

    @Bean
    InsertGate insertGate() {
      return new InsertGate();
    }
  }

  /** 在真正的 MyBatis INSERT 前暂停，之后仍调用真实 SQL，不替换持久化行为。 */
  @Intercepts(
      @Signature(
          type = Executor.class,
          method = "update",
          args = {MappedStatement.class, Object.class}))
  static class InsertGate implements Interceptor {
    final CountDownLatch checked = new CountDownLatch(1);
    final CountDownLatch allowCommit = new CountDownLatch(1);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
      if (statement.getId().equals("com.parking.mapper.PaymentMapper.insert")) {
        checked.countDown();
        if (!allowCommit.await(5, TimeUnit.SECONDS))
          throw new IllegalStateException("Test latch timeout");
      }
      return invocation.proceed();
    }
  }

  static class ControlledClock extends Clock {
    private final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-11-07T00:00:00Z"));

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now.get();
    }

    void advance(Duration duration) {
      now.updateAndGet(at -> at.plus(duration));
    }
  }

  @Test
  void paymentHoldingResourceLockStillBlocksNewBookingAfterDeadline() throws Exception {
    String userId = UUID.randomUUID().toString(), resourceId = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO app_user(id,username,password_hash,display_name,role,created_at) VALUES(?,?,?,?,?,?)",
        userId,
        "boundary",
        "unused",
        "boundary",
        "USER",
        Timestamp.from(clock.instant()));
    db.update(
        "INSERT INTO parking_resource(id,code,park_code,park_name,location,resource_type,hourly_rate,created_at) VALUES(?,?,?,?,?,?,?,?)",
        resourceId,
        "T-001",
        "test",
        "测试",
        "测试",
        "PARKING",
        new BigDecimal("6"),
        Timestamp.from(clock.instant()));
    UserView user = new UserView(userId, "boundary", "测试", Role.USER);
    Instant start = clock.instant().plusSeconds(3600);
    String bookingId =
        bookings
            .create(
                user,
                new CreateBookingRequest(
                    "first", resourceId, "浙A12345", start, start.plusSeconds(3600)))
            .id();
    CountDownLatch competitorStarted = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> payment = pool.submit(() -> bookings.pay(user, bookingId));
      assertThat(gate.checked.await(5, TimeUnit.SECONDS)).isTrue();
      clock.advance(Duration.ofMinutes(6));
      Future<?> competing =
          pool.submit(
              () -> {
                competitorStarted.countDown();
                return bookings.create(
                    user,
                    new CreateBookingRequest(
                        "second", resourceId, "浙A12345", start, start.plusSeconds(3600)));
              });
      assertThat(competitorStarted.await(5, TimeUnit.SECONDS)).isTrue();
      // 支付没有提交时，第二次预约必须等待资源锁，不能把旧单当成过期后直接插入。
      assertThatThrownBy(() -> competing.get(150, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      gate.allowCommit.countDown();
      payment.get(5, TimeUnit.SECONDS);
      assertThatThrownBy(() -> competing.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(BusinessException.class);
      assertThat(db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(1);
      assertThat(
              db.queryForObject("SELECT status FROM booking WHERE id=?", String.class, bookingId))
          .isEqualTo("CONFIRMED");
    } finally {
      gate.allowCommit.countDown();
      pool.shutdownNow();
    }
  }
}
