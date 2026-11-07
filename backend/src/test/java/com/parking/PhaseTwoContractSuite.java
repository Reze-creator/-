package com.parking;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** 同一套事件与结算契约同时运行于 H2 和真实 MySQL。 */
abstract class PhaseTwoContractSuite extends ApiContractSuite {
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.parking.service.SettlementService settlement;

  @Test
  void settlementFailureRollsBackExitAndCanReplaySameEvent() throws Exception {
    String bookingId = paid("rollback-exit");
    send(event("rollback-enter", bookingId, 1, "ENTER", start, "0"));
    Map<String, Object> exit = event("rollback-exit", bookingId, 2, "EXIT", end, "0");
    com.parking.service.SettlementService target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(settlement);
    org.mockito.Mockito.doThrow(new IllegalStateException("Injected settlement failure"))
        .when(target)
        .settle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    try {
      postJson("/api/admin/device-events", exit, adminToken, 500);
      assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_bill", Integer.class)).isZero();
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM device_event WHERE id='rollback-exit'", Integer.class))
          .isZero();
      assertThat(db.queryForObject("SELECT status FROM usage_session", String.class))
          .isEqualTo("PARKED");
      assertThat(
              db.queryForObject("SELECT status FROM booking WHERE id=?", String.class, bookingId))
          .isEqualTo("IN_USE");
    } finally {
      org.mockito.Mockito.reset(target);
    }
    assertThat(send(exit).get("status").asText()).isEqualTo("APPLIED");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_bill", Integer.class)).isEqualTo(1);
  }

  private String paid(String key) throws Exception {
    String id = create(key).get("id").asText();
    postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200);
    return id;
  }

  private Map<String, Object> event(
      String id, String booking, int seq, String type, Instant at, String energy) {
    return Map.of(
        "eventId",
        id,
        "bookingId",
        booking,
        "sequenceNo",
        seq,
        "type",
        type,
        "occurredAt",
        at.toString(),
        "energyKwh",
        energy);
  }

  private JsonNode send(Map<String, Object> event) throws Exception {
    return postJson("/api/admin/device-events", event, adminToken, 200);
  }

  @Test
  void outOfOrderExitWaitsThenSettlesOnce() throws Exception {
    String b = paid("out-of-order");
    Map<String, Object> exit = event("exit-early", b, 2, "EXIT", start.plusSeconds(5400), "0");
    assertThat(send(exit).get("status").asText()).isEqualTo("WAITING");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_bill", Integer.class)).isZero();
    send(event("enter-later", b, 1, "ENTER", start, "0"));
    assertThat(send(exit).get("status").asText()).isEqualTo("APPLIED");
    JsonNode bill = getJson("/api/bills", userToken, 200).get("items").get(0);
    assertThat(bill.get("totalAmount").decimalValue()).isEqualByComparingTo("9.00");
    assertThat(bill.get("prepaidAmount").decimalValue()).isEqualByComparingTo("6.00");
    assertThat(bill.get("additionalAmount").decimalValue()).isEqualByComparingTo("3.00");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_bill", Integer.class)).isEqualTo(1);
    assertThat(getJson("/api/bookings/" + b, userToken, 200).get("status").asText())
        .isEqualTo("COMPLETED");
  }

  @Test
  void chargingUsesCumulativeMeterAndPriceSnapshot() throws Exception {
    db.update("UPDATE parking_resource SET resource_type='CHARGING' WHERE id=?", resourceId);
    String b = paid("charging");
    send(event("charge-enter", b, 1, "ENTER", start, "0"));
    send(event("charge-start", b, 2, "CHARGE_STARTED", start.plusSeconds(1), "0"));
    Map<String, Object> meter =
        event("meter", b, 3, "METER_REPORTED", start.plusSeconds(600), "5.500");
    send(meter);
    send(meter);
    send(event("charge-stop", b, 4, "CHARGE_STOPPED", start.plusSeconds(3000), "10.125"));
    db.update("UPDATE parking_resource SET hourly_rate=99 WHERE id=?", resourceId);
    send(event("charge-exit", b, 5, "EXIT", start.plusSeconds(3600), "10.125"));
    JsonNode bill = getJson("/api/bills", userToken, 200).get("items").get(0);
    assertThat(bill.get("energyAmount").decimalValue()).isEqualByComparingTo("12.15");
    assertThat(bill.get("parkingAmount").decimalValue()).isEqualByComparingTo("6.00");
    assertThat(bill.get("totalAmount").decimalValue()).isEqualByComparingTo("18.15");
    assertThat(bill.get("energyRate").decimalValue()).isEqualByComparingTo("1.20");
  }

  @Test
  void shortParkingRefundsDifferenceWithoutCancellingReservation() throws Exception {
    String b = paid("refund-difference");
    send(event("refund-enter", b, 1, "ENTER", start, "0"));
    send(event("refund-exit", b, 2, "EXIT", start.plusSeconds(1800), "0"));
    JsonNode bill = getJson("/api/bills", userToken, 200).get("items").get(0);
    assertThat(bill.get("refundAmount").decimalValue()).isEqualByComparingTo("3.00");
    assertThat(getJson("/api/bookings/" + b, userToken, 200).get("paymentStatus").asText())
        .isEqualTo("SETTLED");
    postJson("/api/bookings/" + b + "/cancel", Map.of(), userToken, 409);
  }

  @Test
  void eventIdAndSequenceCannotBeReusedWithChangedInput() throws Exception {
    String b = paid("event-key");
    Map<String, Object> first = event("same-event", b, 1, "ENTER", start, "0");
    send(first);
    postJson(
        "/api/admin/device-events",
        event("same-event", b, 1, "ENTER", start.plusSeconds(1), "0"),
        adminToken,
        409);
    postJson(
        "/api/admin/device-events",
        event("different-id", b, 1, "ENTER", start, "0"),
        adminToken,
        409);
    assertThat(send(first).get("status").asText()).isEqualTo("APPLIED");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_session", Integer.class)).isEqualTo(1);
  }

  @Test
  void invalidEventBlocksQueueUntilOperatorSkipsWithAudit() throws Exception {
    String b = paid("failed-queue");
    send(event("failed-enter", b, 1, "ENTER", start, "0"));
    assertThat(
            send(event("invalid-charge", b, 2, "CHARGE_STARTED", start.plusSeconds(1), "0"))
                .get("status")
                .asText())
        .isEqualTo("FAILED");
    assertThat(
            send(event("after-invalid", b, 3, "EXIT", start.plusSeconds(3600), "0"))
                .get("status")
                .asText())
        .isEqualTo("WAITING");
    JsonNode incident =
        getJson("/api/admin/operations/incidents", adminToken, 200).get("items").get(0);
    String path = "/api/admin/operations/incidents/" + incident.get("id").asText() + "/resolve";
    postJson(path, Map.of("resolution", "未处理不能关单"), adminToken, 409);
    postJson(
        "/api/admin/device-events/invalid-charge/action",
        Map.of("action", "RETRY", "reason", "确认仍不满足类型"),
        adminToken,
        200);
    postJson(
        "/api/admin/device-events/invalid-charge/action",
        Map.of("action", "SKIP", "reason", "普通车位误发充电开始"),
        adminToken,
        200);
    postJson(path, Map.of("resolution", "跳过误报并补齐离场"), adminToken, 200);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_bill", Integer.class)).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM operation_audit WHERE action='SKIP_EVENT'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void regressingTimeOrMeterCannotMutateSession() throws Exception {
    db.update("UPDATE parking_resource SET resource_type='CHARGING' WHERE id=?", resourceId);
    String b = paid("regress");
    send(event("r-enter", b, 1, "ENTER", start, "0"));
    send(event("r-start", b, 2, "CHARGE_STARTED", start.plusSeconds(1), "0"));
    send(event("r-meter", b, 3, "METER_REPORTED", start.plusSeconds(60), "10"));
    assertThat(
            send(event("r-back", b, 4, "METER_REPORTED", start.plusSeconds(61), "9"))
                .get("status")
                .asText())
        .isEqualTo("FAILED");
    assertThat(
            db.queryForObject("SELECT energy_kwh FROM usage_session", java.math.BigDecimal.class))
        .isEqualByComparingTo("10");
    assertThat(
            db.queryForObject(
                "SELECT last_event_sequence FROM booking WHERE id=?", Integer.class, b))
        .isEqualTo(3);
  }

  @Test
  void concurrentExitDeliveryDoesNotDuplicateBill() throws Exception {
    String b = paid("concurrent-exit");
    send(event("concurrent-enter", b, 1, "ENTER", start, "0"));
    Map<String, Object> exit = event("concurrent-exit", b, 2, "EXIT", start.plusSeconds(3600), "0");
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 10; i++) tasks.add(() -> send(exit).get("status").asText());
      for (Future<String> result : pool.invokeAll(tasks))
        assertThat(result.get()).isEqualTo("APPLIED");
    } finally {
      pool.shutdownNow();
    }
    assertThat(db.queryForObject("SELECT COUNT(*) FROM usage_bill", Integer.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM device_event", Integer.class)).isEqualTo(2);
  }

  @Test
  void sessionsAndBillsAreOwnerScopedAndSimulatorIsAdminOnly() throws Exception {
    String b = paid("usage-permissions");
    Map<String, Object> enter = event("permission-enter", b, 1, "ENTER", start, "0");
    postJson("/api/admin/device-events", enter, userToken, 403);
    send(enter);
    assertThat(getJson("/api/sessions", otherToken, 200).get("total").asInt()).isZero();
    assertThat(getJson("/api/admin/sessions", adminToken, 200).get("total").asInt()).isEqualTo(1);
    send(event("permission-exit", b, 2, "EXIT", start.plusSeconds(3600), "0"));
    assertThat(getJson("/api/bills", otherToken, 200).get("total").asInt()).isZero();
    getJson("/api/admin/operations/overview", otherToken, 403);
    getJson("/api/sessions?page=0", userToken, 400);
  }

  @Test
  void activeSessionPreventsSecondPhysicalEntryOnSameResource() throws Exception {
    String first = paid("active-first");
    String second =
        postJson(
                "/api/bookings",
                booking("active-second", end, end.plusSeconds(3600)),
                userToken,
                200)
            .get("id")
            .asText();
    postJson("/api/bookings/" + second + "/pay", Map.of(), userToken, 200);
    send(event("active-enter-one", first, 1, "ENTER", start, "0"));
    assertThat(send(event("active-enter-two", second, 1, "ENTER", end, "0")).get("status").asText())
        .isEqualTo("FAILED");
    send(event("active-exit-one", first, 2, "EXIT", end, "0"));
    assertThat(
            postJson(
                    "/api/admin/device-events/active-enter-two/action",
                    Map.of("action", "RETRY", "reason", "前车已离场"),
                    adminToken,
                    200)
                .get("status")
                .asText())
        .isEqualTo("APPLIED");
    postJson("/api/bookings/" + second + "/cancel", Map.of(), userToken, 409);
  }

  @Test
  void offlineAndFaultIncidentsRequireRecoveryBeforeClosing() throws Exception {
    String path = "/api/admin/devices/" + resourceId + "/heartbeat";
    postJson(path, Map.of("fault", true, "message", "模拟故障"), adminToken, 200);
    postJson(path, Map.of("fault", true, "message", "重复故障心跳"), adminToken, 200);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM operation_incident", Integer.class))
        .isEqualTo(1);
    String id =
        getJson("/api/admin/operations/incidents", adminToken, 200)
            .get("items")
            .get(0)
            .get("id")
            .asText();
    postJson(
        "/api/admin/operations/incidents/" + id + "/resolve",
        Map.of("resolution", "尚未恢复"),
        adminToken,
        409);
    postJson(path, Map.of("fault", false), adminToken, 200);
    postJson(
        "/api/admin/operations/incidents/" + id + "/resolve",
        Map.of("resolution", "恢复正常心跳"),
        adminToken,
        200);
    db.update(
        "UPDATE simulated_device SET last_seen_at=?",
        Timestamp.from(Instant.now().minusSeconds(180)));
    postJson("/api/admin/operations/scan", Map.of(), adminToken, 200);
    assertThat(getJson("/api/admin/devices", adminToken, 200).get(0).get("status").asText())
        .isEqualTo("OFFLINE");
    assertThat(
            getJson("/api/admin/operations/overview", adminToken, 200).get("openIncidents").asInt())
        .isEqualTo(1);
  }

  @Test
  void missingSequenceCreatesOneIncidentOnRepeatedScan() throws Exception {
    String b = paid("gap");
    send(event("gap-exit", b, 2, "EXIT", end, "0"));
    db.update(
        "UPDATE device_event SET created_at=?", Timestamp.from(Instant.now().minusSeconds(45)));
    postJson("/api/admin/operations/scan", Map.of(), adminToken, 200);
    postJson("/api/admin/operations/scan", Map.of(), adminToken, 200);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM operation_incident WHERE source_type='EVENT_GAP'",
                Integer.class))
        .isEqualTo(1);
    send(event("gap-enter", b, 1, "ENTER", start, "0"));
    String id =
        getJson("/api/admin/operations/incidents", adminToken, 200)
            .get("items")
            .get(0)
            .get("id")
            .asText();
    postJson(
        "/api/admin/operations/incidents/" + id + "/resolve",
        Map.of("resolution", "补齐前序事件"),
        adminToken,
        200);
  }

  @Test
  void billingRoundsPartialMinuteAndDoesNotChargeEnergyWithoutCharging() throws Exception {
    String b = paid("minute-rounding");
    send(event("minute-enter", b, 1, "ENTER", start, "0"));
    send(event("minute-exit", b, 2, "EXIT", start.plusSeconds(61), "0"));
    JsonNode bill = getJson("/api/bills", userToken, 200).get("items").get(0);
    assertThat(bill.get("parkingMinutes").asLong()).isEqualTo(2);
    assertThat(bill.get("parkingAmount").decimalValue()).isEqualByComparingTo("0.20");
    assertThat(bill.get("energyAmount").decimalValue()).isEqualByComparingTo("0.00");
  }
}
