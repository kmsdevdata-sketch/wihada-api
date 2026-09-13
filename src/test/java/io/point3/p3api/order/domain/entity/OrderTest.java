package io.point3.p3api.order.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.point3.p3api.order.domain.type.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

  @Test
  @DisplayName("주문은 결제 완료 상태와 주문 스냅샷으로 생성된다")
  void createsPaidOrderWithSnapshots() {
    Instant pickupAt = Instant.parse("2026-08-30T04:30:00Z");

    Order order = Order.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "P3-20260830-0001",
        "초코 케이크",
        "초코 시트",
        38000,
        pickupAt);

    assertEquals(OrderStatus.PAID, order.getStatus());
    assertEquals("P3-20260830-0001", order.getOrderNumber());
    assertEquals("초코 케이크", order.getMenuNameSnapshot());
    assertEquals("[]", order.getStartReferenceAssets());
    assertEquals(38000, order.getPaidAmount());
    assertEquals(pickupAt, order.getPickupAt());
  }

  @Test
  @DisplayName("주문은 주문 시작 참조 이미지 스냅샷을 함께 생성할 수 있다")
  void createsPaidOrderWithStartReferenceAssets() {
    UUID assetId = UUID.randomUUID();

    Order order = Order.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "P3-20260830-0001",
        "초코 케이크",
        "초코 시트",
        "[\"" + assetId + "\"]",
        38000,
        Instant.parse("2026-08-30T04:30:00Z"));

    assertEquals("[\"" + assetId + "\"]", order.getStartReferenceAssets());
  }

  @Test
  @DisplayName("구매자 환불 요청은 주문을 환불 요청 상태로 변경한다")
  void requestsRefund() {
    Order order = createOrder();
    Instant requestedAt = Instant.parse("2026-08-30T04:30:00Z");

    order.requestRefund("픽업 일정 변경", requestedAt);

    assertEquals(OrderStatus.REFUND_REQUESTED, order.getStatus());
    assertEquals("픽업 일정 변경", order.getRefundReason());
    assertEquals(requestedAt, order.getRefundRequestedAt());
  }

  @Test
  @DisplayName("환불 완료는 환불 요청 주문을 환불 완료 상태로 변경한다")
  void refundsRequestedOrder() {
    Order order = createOrder();
    order.requestRefund("픽업 일정 변경", Instant.parse("2026-08-30T04:30:00Z"));

    order.refund("판매자 환불 처리");

    assertEquals(OrderStatus.REFUNDED, order.getStatus());
    assertEquals("판매자 환불 처리", order.getRefundReason());
  }

  @Test
  @DisplayName("판매자는 결제완료 주문을 직접 환불 완료로 변경할 수 있다")
  void refundsPaidOrderDirectly() {
    Order order = createOrder();

    order.refund("판매자 환불 처리");

    assertEquals(OrderStatus.REFUNDED, order.getStatus());
    assertEquals("판매자 환불 처리", order.getRefundReason());
  }

  @Test
  @DisplayName("음수 결제 금액의 주문은 생성할 수 없다")
  void rejectsNegativePaidAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Order.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "P3-20260830-0001",
            "초코 케이크",
            "초코 시트",
            -1,
            Instant.parse("2026-08-30T04:30:00Z")));
  }

  private Order createOrder() {
    return Order.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "P3-20260830-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
        "초코 케이크",
        "초코 시트",
        38000,
        Instant.parse("2026-08-30T04:30:00Z"));
  }
}
