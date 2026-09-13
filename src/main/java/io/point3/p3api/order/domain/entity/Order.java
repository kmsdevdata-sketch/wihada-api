package io.point3.p3api.order.domain.entity;

import io.point3.p3api.order.domain.type.OrderStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "buyer_user_id", nullable = false)
  private UUID buyerUserId;

  @Column(name = "inquiry_id", nullable = false)
  private UUID inquiryId;

  @Column(name = "confirmation_id", nullable = false)
  private UUID confirmationId;

  @Column(name = "payment_attempt_id", nullable = false, unique = true)
  private UUID paymentAttemptId;

  @Column(name = "order_number", nullable = false, unique = true, length = 40)
  private String orderNumber;

  @Column(name = "menu_name_snapshot", nullable = false, length = 150)
  private String menuNameSnapshot;

  @Column(name = "option_summary_snapshot", nullable = false, columnDefinition = "text")
  private String optionSummarySnapshot;

  @Column(name = "start_reference_assets", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String startReferenceAssets;

  @Column(name = "paid_amount", nullable = false)
  private long paidAmount;

  @Column(name = "pickup_at", nullable = false)
  private Instant pickupAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private OrderStatus status;

  @Column(name = "refund_requested_at")
  private Instant refundRequestedAt;

  @Column(name = "refund_reason", columnDefinition = "text")
  private String refundReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private Order(
      UUID storeId,
      UUID buyerUserId,
      UUID inquiryId,
      UUID confirmationId,
      UUID paymentAttemptId,
      String orderNumber,
      String menuNameSnapshot,
      String optionSummarySnapshot,
      String startReferenceAssets,
      long paidAmount,
      Instant pickupAt) {
    this.storeId = storeId;
    this.buyerUserId = buyerUserId;
    this.inquiryId = inquiryId;
    this.confirmationId = confirmationId;
    this.paymentAttemptId = paymentAttemptId;
    this.orderNumber = orderNumber;
    this.menuNameSnapshot = menuNameSnapshot;
    this.optionSummarySnapshot = optionSummarySnapshot;
    this.startReferenceAssets = startReferenceAssets;
    this.paidAmount = paidAmount;
    this.pickupAt = pickupAt;
    this.status = OrderStatus.PAID;
  }

  public static Order create(
      UUID storeId,
      UUID buyerUserId,
      UUID inquiryId,
      UUID confirmationId,
      UUID paymentAttemptId,
      String orderNumber,
      String menuNameSnapshot,
      String optionSummarySnapshot,
      long paidAmount,
      Instant pickupAt) {
    return create(
        storeId,
        buyerUserId,
        inquiryId,
        confirmationId,
        paymentAttemptId,
        orderNumber,
        menuNameSnapshot,
        optionSummarySnapshot,
        "[]",
        paidAmount,
        pickupAt);
  }

  public static Order create(
      UUID storeId,
      UUID buyerUserId,
      UUID inquiryId,
      UUID confirmationId,
      UUID paymentAttemptId,
      String orderNumber,
      String menuNameSnapshot,
      String optionSummarySnapshot,
      String startReferenceAssets,
      long paidAmount,
      Instant pickupAt) {
    Objects.requireNonNull(storeId, "storeId");
    Objects.requireNonNull(buyerUserId, "buyerUserId");
    Objects.requireNonNull(inquiryId, "inquiryId");
    Objects.requireNonNull(confirmationId, "confirmationId");
    Objects.requireNonNull(paymentAttemptId, "paymentAttemptId");
    Objects.requireNonNull(orderNumber, "orderNumber");
    Objects.requireNonNull(menuNameSnapshot, "menuNameSnapshot");
    Objects.requireNonNull(optionSummarySnapshot, "optionSummarySnapshot");
    Objects.requireNonNull(startReferenceAssets, "startReferenceAssets");
    Objects.requireNonNull(pickupAt, "pickupAt");

    if (paidAmount < 0) {
      throw new IllegalArgumentException("paidAmount must be greater than or equal to 0");
    }

    return new Order(
        storeId,
        buyerUserId,
        inquiryId,
        confirmationId,
        paymentAttemptId,
        orderNumber,
        menuNameSnapshot,
        optionSummarySnapshot,
        startReferenceAssets,
        paidAmount,
        pickupAt);
  }

  public void markPickedUp() {
    ensureStatus(OrderStatus.PAID);
    this.status = OrderStatus.PICKED_UP;
  }

  public void requestRefund(String refundReason, Instant requestedAt) {
    Objects.requireNonNull(refundReason, "refundReason");
    Objects.requireNonNull(requestedAt, "requestedAt");
    if (refundReason.isBlank()) {
      throw new IllegalArgumentException("refundReason must not be blank");
    }

    ensureStatus(OrderStatus.PAID);
    this.status = OrderStatus.REFUND_REQUESTED;
    this.refundReason = refundReason;
    this.refundRequestedAt = requestedAt;
  }

  public void refund(String refundReason) {
    Objects.requireNonNull(refundReason, "refundReason");
    if (refundReason.isBlank()) {
      throw new IllegalArgumentException("refundReason must not be blank");
    }

    validateRefundable();

    this.status = OrderStatus.REFUNDED;
    this.refundReason = refundReason;
  }

  public void validateRefundable() {
    if (this.status != OrderStatus.PAID && this.status != OrderStatus.REFUND_REQUESTED) {
      throw new IllegalStateException("Order status transition is not allowed");
    }
  }

  private void ensureStatus(OrderStatus expectedStatus) {
    if (this.status != expectedStatus) {
      throw new IllegalStateException("Order status transition is not allowed");
    }
  }
}
