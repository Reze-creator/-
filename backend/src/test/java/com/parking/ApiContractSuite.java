package com.parking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Shared contract: run against H2 for fast feedback and MySQL for actual row-lock behavior. */
abstract class ApiContractSuite extends ApiFixture {
  @Test
  void registerAlwaysCreatesOrdinaryUserAndNeverReturnsHash() throws Exception {
    JsonNode registered =
        postJson(
            "/api/auth/register",
            Map.of("username", "new_user", "password", "Good@123", "displayName", "新用户"),
            null,
            201);
    assertThat(registered.at("/user/role").asText()).isEqualTo("USER");
    assertThat(registered.toString()).doesNotContain("passwordHash", "password_hash");
    postJson(
        "/api/auth/register",
        Map.of("username", "new_user", "password", "Good@123", "displayName", "重复"),
        null,
        409);
    postJson(
        "/api/auth/register",
        Map.of("username", "hacker", "password", "Good@123", "displayName", "x", "role", "ADMIN"),
        null,
        400);
  }

  @Test
  void loginLogoutAndSessionExpiryAreEnforced() throws Exception {
    getJson("/api/auth/me", userToken, 200);
    postJson("/api/auth/login", Map.of("username", "alice", "password", "wrong123"), null, 401);
    postJson("/api/auth/logout", Map.of(), userToken, 200);
    getJson("/api/auth/me", userToken, 401);
    db.update(
        "UPDATE user_session SET expires_at = ?", Timestamp.from(Instant.now().minusSeconds(5)));
    getJson("/api/auth/me", otherToken, 401);
  }

  @Test
  void publicResourcesAndHealthDoNotRequireLogin() throws Exception {
    assertThat(getJson("/api/parks", null, 200).size()).isEqualTo(1);
    assertThat(getJson("/api/resources", null, 200).size()).isEqualTo(1);
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/bookings']").exists());
    getJson("/api/bookings", null, 401);
  }

  @Test
  void bookingAmountUsesExactHalfHourAndPriceSnapshot() throws Exception {
    JsonNode result =
        postJson("/api/bookings", booking("half", start, start.plusSeconds(5400)), userToken, 200);
    assertThat(result.get("amount").decimalValue()).isEqualByComparingTo("9.00");
    assertThat(result.get("status").asText()).isEqualTo("PENDING_PAYMENT");
    db.update("UPDATE parking_resource SET hourly_rate=20 WHERE id=?", resourceId);
    assertThat(
            getJson("/api/bookings/" + result.get("id").asText(), userToken, 200)
                .get("hourlyRate")
                .decimalValue())
        .isEqualByComparingTo("6.00");
  }

  @Test
  void overlappingIntervalsAreRejectedButAdjacentAreAllowed() throws Exception {
    create("first");
    for (Instant[] range :
        List.of(
            new Instant[] {start, end},
            new Instant[] {start.minusSeconds(1800), start.plusSeconds(1800)},
            new Instant[] {end.minusSeconds(1800), end.plusSeconds(1800)},
            new Instant[] {start.minusSeconds(1800), end.plusSeconds(1800)}))
      postJson(
          "/api/bookings",
          booking(UUID.randomUUID().toString(), range[0], range[1]),
          otherToken,
          409);
    postJson("/api/bookings", booking("adjacent", end, end.plusSeconds(1800)), otherToken, 200);
  }

  @Test
  void concurrentOverlappingRequestsProduceExactlyOneReservation() throws Exception {
    List<Integer> statuses =
        concurrent(
            10,
            () ->
                mvc.perform(
                        post("/api/bookings")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType("application/json")
                            .content(
                                json.writeValueAsBytes(
                                    booking(UUID.randomUUID().toString(), start, end))))
                    .andReturn()
                    .getResponse()
                    .getStatus());
    assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
    assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(9);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(1);
  }

  @Test
  void concurrentSameRequestIsIdempotent() throws Exception {
    List<JsonNode> results =
        concurrent(
            10, () -> postJson("/api/bookings", booking("same", start, end), userToken, 200));
    assertThat(results.stream().map(n -> n.get("id").asText()).distinct().count()).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(1);
  }

  @Test
  void replayDoesNotExtendHoldAndChangedInputIsConflict() throws Exception {
    JsonNode first = create("same");
    JsonNode replay = create("same");
    assertThat(replay).isEqualTo(first);
    Map<String, Object> changed = booking("same", start, end);
    changed.put("plateNumber", "浙B12345");
    postJson("/api/bookings", changed, userToken, 409);
  }

  @Test
  void sameRequestKeyCanBeUsedByDifferentUsers() throws Exception {
    create("same");
    postJson("/api/bookings", booking("same", end, end.plusSeconds(1800)), otherToken, 200);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(2);
  }

  @Test
  void paymentAndRefundAreAtomicAndIdempotent() throws Exception {
    String id = create("payment").get("id").asText();
    assertThat(
            postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200)
                .get("status")
                .asText())
        .isEqualTo("CONFIRMED");
    postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM mock_payment", Integer.class)).isEqualTo(1);
    JsonNode cancelled = postJson("/api/bookings/" + id + "/cancel", Map.of(), userToken, 200);
    assertThat(cancelled.get("paymentStatus").asText()).isEqualTo("REFUNDED");
    postJson("/api/bookings/" + id + "/cancel", Map.of(), userToken, 200);
    postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 409);
    postJson("/api/bookings", booking("replacement", start, end), otherToken, 200);
  }

  @Test
  void concurrentPaymentDoesNotCreateMultiplePaymentRows() throws Exception {
    String id = create("pay").get("id").asText();
    concurrent(10, () -> postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200));
    assertThat(db.queryForObject("SELECT COUNT(*) FROM mock_payment", Integer.class)).isEqualTo(1);
  }

  @Test
  void payCancelRaceEndsCancelledAndNeverLeavesSuccessfulCharge() throws Exception {
    String id = create("race").get("id").asText();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> pay =
          executor.submit(
              () ->
                  mvc.perform(
                          post("/api/bookings/" + id + "/pay")
                              .header("Authorization", "Bearer " + userToken))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      Future<JsonNode> cancel =
          executor.submit(
              () -> postJson("/api/bookings/" + id + "/cancel", Map.of(), userToken, 200));
      assertThat(pay.get(20, TimeUnit.SECONDS)).isIn(200, 409);
      cancel.get(20, TimeUnit.SECONDS);
      assertThat(getJson("/api/bookings/" + id, userToken, 200).get("status").asText())
          .isEqualTo("CANCELLED");
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM mock_payment WHERE status='SUCCESS'", Integer.class))
          .isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void expiredHoldDoesNotNeedSchedulerToReleaseResourceOrRejectPayment() throws Exception {
    String id = create("expire").get("id").asText();
    db.update(
        "UPDATE booking SET hold_until=? WHERE id=?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    assertThat(
            postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200)
                .get("status")
                .asText())
        .isEqualTo("EXPIRED");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM mock_payment", Integer.class)).isZero();
    postJson("/api/bookings", booking("next", start, end), otherToken, 200);
  }

  @Test
  void cancellationAfterStartIsRejected() throws Exception {
    String id = create("start").get("id").asText();
    postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200);
    db.update(
        "UPDATE booking SET start_at=? WHERE id=?",
        Timestamp.from(Instant.now().minusSeconds(2)),
        id);
    postJson("/api/bookings/" + id + "/cancel", Map.of(), userToken, 409);
  }

  @Test
  void usersCannotReadPayOrCancelSomeoneElsesBooking() throws Exception {
    String id = create("private").get("id").asText();
    getJson("/api/bookings/" + id, otherToken, 404);
    postJson("/api/bookings/" + id + "/pay", Map.of(), otherToken, 404);
    postJson("/api/bookings/" + id + "/cancel", Map.of(), otherToken, 404);
    getJson("/api/bookings/" + id, adminToken, 200);
    postJson("/api/bookings/" + id + "/cancel", Map.of(), adminToken, 404);
    assertThat(getJson("/api/bookings", otherToken, 200).get("total").asInt()).isZero();
  }

  @Test
  void adminCanDisableResourcesAndSeeAllBookings() throws Exception {
    String id = create("existing").get("id").asText();
    mvc.perform(
            patch("/api/admin/resources/" + resourceId + "/status")
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .content("{\"enabled\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/admin/resources/" + resourceId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk());
    assertThat(getJson("/api/resources", null, 200).size()).isZero();
    postJson("/api/bookings", booking("new", end, end.plusSeconds(1800)), userToken, 409);
    postJson("/api/bookings/" + id + "/pay", Map.of(), userToken, 200);
    assertThat(getJson("/api/admin/bookings", adminToken, 200).get("total").asInt()).isEqualTo(1);
    getJson("/api/admin/bookings", userToken, 403);
  }

  @Test
  void validationRejectsInvalidWindowsAndUnknownFields() throws Exception {
    postJson(
        "/api/bookings",
        booking("past", start.minusSeconds(86400), end.minusSeconds(86400)),
        userToken,
        400);
    postJson("/api/bookings", booking("reverse", end, start), userToken, 400);
    postJson("/api/bookings", booking("alignment", start.plusSeconds(1), end), userToken, 400);
    postJson("/api/bookings", booking("long", start, start.plusSeconds(25 * 3600)), userToken, 400);
    Map<String, Object> bad = booking("unknown", start, end);
    bad.put("amount", 1);
    postJson("/api/bookings", bad, userToken, 400);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isZero();
  }

  @Test
  void paginationAndFiltersAreValidated() throws Exception {
    create("one");
    assertThat(
            getJson("/api/bookings?status=PENDING_PAYMENT&page=1&size=1", userToken, 200)
                .get("total")
                .asInt())
        .isEqualTo(1);
    getJson("/api/bookings?size=101", userToken, 400);
    getJson("/api/bookings?page=0", userToken, 400);
    getJson("/api/bookings?status=INVALID", userToken, 400);
    getJson("/api/resources?startAt=" + start, null, 400);
  }

  @Test
  void resourcesAvailabilityReflectsBookings() throws Exception {
    create("busy");
    String query = "?startAt=" + start + "&endAt=" + end;
    assertThat(getJson("/api/resources" + query, null, 200).get(0).get("available").asBoolean())
        .isFalse();
    assertThat(
            getJson("/api/resources?startAt=" + end + "&endAt=" + end.plusSeconds(1800), null, 200)
                .get(0)
                .get("available")
                .asBoolean())
        .isTrue();
  }

  private <T> List<T> concurrent(int count, Callable<T> action) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(count);
    CountDownLatch gate = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++)
        futures.add(
            executor.submit(
                () -> {
                  gate.await();
                  return action.call();
                }));
      gate.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    } finally {
      executor.shutdownNow();
    }
  }
}
