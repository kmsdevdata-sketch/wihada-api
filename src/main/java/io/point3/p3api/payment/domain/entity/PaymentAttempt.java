package io.point3.p3api.payment.domain.entity;

import io.point3.p3api.payment.domain.type.PaymentAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "payment_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "confirmation_id", nullable = false)
  private UUID confirmationId;

  @Column(name = "payer_user_id", nullable = false)
  private UUID payerUserId;

  @Column(name = "point3_session_id", nullable = false, unique = true, length = 128)
  private String point3SessionId;

  @Column(name = "payer_id", length = 128)
  private String payerId;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private PaymentAttemptStatus status;

  @Column(name = "failure_code", length = 100)
  private String failureCode;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "capture_started_at")
  private Instant captureStartedAt;

  @Column(name = "reconciliation_checked_at")
  private Instant reconciliationCheckedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  private PaymentAttempt(
      UUID confirmationId,
      UUID payerUserId,
      String point3SessionId,
      String payerId,
      long amount,
      Instant expiresAt) {
    this.confirmationId = confirmationId;
    this.payerUserId = payerUserId;
    this.point3SessionId = point3SessionId;
    this.payerId = payerId;
    this.amount = amount;
    this.expiresAt = expiresAt;
    this.status = PaymentAttemptStatus.READY;
  }

  public static PaymentAttempt create(
      UUID confirmationId,
      UUID payerUserId,
      String point3SessionId,
      String payerId,
      long amount,
      Instant expiresAt) {
    Objects.requireNonNull(confirmationId, "confirmationId");
    Objects.requireNonNull(payerUserId, "payerUserId");
    Objects.requireNonNull(point3SessionId, "point3SessionId");
    Objects.requireNonNull(expiresAt, "expiresAt");

    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be greater than 0");
    }

    return new PaymentAttempt(
        confirmationId, payerUserId, point3SessionId, payerId, amount, expiresAt);
  }

  public void startCapture(String payerId, Instant startedAt) {
    Objects.requireNonNull(payerId, "payerId");
    Objects.requireNonNull(startedAt, "startedAt");
    this.payerId = payerId;
    this.captureStartedAt = startedAt;
    this.status = PaymentAttemptStatus.IN_PROGRESS;
    this.failureCode = null;
  }

  public void needConfirmation(String failureCode, Instant completedAt) {
    Objects.requireNonNull(completedAt, "completedAt");
    this.failureCode = failureCode;
    this.status = PaymentAttemptStatus.NEEDS_CONFIRMATION;
    this.completedAt = completedAt;
    this.reconciliationCheckedAt = completedAt;
  }

  public void markReconciliationChecked(Instant checkedAt) {
    this.reconciliationCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt");
  }

  public void succeed(String payerId, Instant completedAt) {
    Objects.requireNonNull(payerId, "payerId");
    Objects.requireNonNull(completedAt, "completedAt");
    this.payerId = payerId;
    this.status = PaymentAttemptStatus.SUCCEEDED;
    this.completedAt = completedAt;
  }

  public void fail(String failureCode, Instant completedAt) {
    Objects.requireNonNull(completedAt, "completedAt");
    this.failureCode = failureCode;
    this.status = PaymentAttemptStatus.FAILED;
    this.completedAt = completedAt;
  }

  public boolean isReady() {
    return this.status == PaymentAttemptStatus.READY;
  }

  public boolean isActive(Instant now) {
    Objects.requireNonNull(now, "now");
    if (this.status == PaymentAttemptStatus.IN_PROGRESS
        || this.status == PaymentAttemptStatus.NEEDS_CONFIRMATION) {
      return true;
    }
    return this.status == PaymentAttemptStatus.READY && !now.isAfter(this.expiresAt);
  }

  public boolean needsConfirmation() {
    return this.status == PaymentAttemptStatus.NEEDS_CONFIRMATION;
  }
}
