package io.point3.p3api.payment.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.point3.p3api.payment.domain.type.PaymentAttemptStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentAttemptTest {

  @Test
  @DisplayName("결제시도는 READY로 생성되고 성공 시 payerId와 완료 시각을 기록한다")
  void succeedsPaymentAttempt() {
    PaymentAttempt paymentAttempt = paymentAttempt();
    Instant completedAt = Instant.parse("2026-08-25T00:00:00Z");

    paymentAttempt.succeed("payer-123", completedAt);

    assertEquals(PaymentAttemptStatus.SUCCEEDED, paymentAttempt.getStatus());
    assertEquals("payer-123", paymentAttempt.getPayerId());
    assertEquals(completedAt, paymentAttempt.getCompletedAt());
  }

  @Test
  @DisplayName("결제시도 실패 시 실패 코드와 완료 시각을 기록한다")
  void failsPaymentAttempt() {
    PaymentAttempt paymentAttempt = paymentAttempt();
    Instant completedAt = Instant.parse("2026-08-25T00:00:00Z");

    paymentAttempt.fail("POINT3_TIMEOUT", completedAt);

    assertEquals(PaymentAttemptStatus.FAILED, paymentAttempt.getStatus());
    assertEquals("POINT3_TIMEOUT", paymentAttempt.getFailureCode());
    assertEquals(completedAt, paymentAttempt.getCompletedAt());
  }

  @Test
  @DisplayName("음수 금액의 결제시도는 생성할 수 없다")
  void rejectsNegativeAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PaymentAttempt.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "pymt_sess-1",
            null,
            -1,
            Instant.parse("2026-08-25T01:00:00Z")));
  }

  @Test
  @DisplayName("결과가 미확정된 결제시도는 세션 만료 후에도 재사용 대상으로 판단한다")
  void checksActivePaymentAttempt() {
    Instant now = Instant.parse("2026-08-25T00:00:00Z");
    PaymentAttempt ready = paymentAttempt();
    PaymentAttempt inProgress = paymentAttempt();
    PaymentAttempt needsConfirmation = paymentAttempt();
    PaymentAttempt expired = PaymentAttempt.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "pymt_sess-" + UUID.randomUUID(),
        null,
        38000,
        Instant.parse("2026-08-24T23:59:59Z"));
    PaymentAttempt failed = paymentAttempt();

    inProgress.startCapture("payer-1", now);
    needsConfirmation.needConfirmation("POINT3_PROCESSING", now);
    failed.fail("POINT3_FAILED", now);

    assertTrue(ready.isActive(now));
    assertTrue(inProgress.isActive(now));
    assertTrue(inProgress.isActive(now.plus(Duration.ofHours(25))));
    assertTrue(needsConfirmation.isActive(now.plus(Duration.ofHours(25))));
    assertFalse(expired.isActive(now));
    assertFalse(failed.isActive(now));
  }

  private PaymentAttempt paymentAttempt() {
    return PaymentAttempt.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "pymt_sess-" + UUID.randomUUID(),
        null,
        38000,
        Instant.parse("2026-08-25T01:00:00Z"));
  }
}
