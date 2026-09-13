package io.point3.p3api.order.application.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.CommonErrorCode;
import io.point3.p3api.exception.code.OrderErrorCode;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.store.application.refundpolicy.port.StoreRefundPolicyPersistencePort;
import io.point3.p3api.store.domain.entity.StoreRefundPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderRefundCalculationResolverTest {

  private static final UUID STORE_ID = UUID.randomUUID();
  private static final Instant PICKUP_AT = Instant.parse("2030-09-15T03:00:00Z");
  private static final Instant REQUESTED_AT = Instant.parse("2030-09-08T03:00:00Z");
  private static final Instant PROCESSING_AT = Instant.parse("2030-09-10T03:00:00Z");

  private final StoreRefundPolicyPersistencePort refundPolicyPersistencePort =
      mock(StoreRefundPolicyPersistencePort.class);
  private final OrderRefundCalculationResolver resolver =
      new OrderRefundCalculationResolver(
          new OrderRefundPolicyCalculator(), refundPolicyPersistencePort);

  @Test
  @DisplayName("환불 요청 주문은 구매자 요청 시각 기준으로 환불금액을 계산한다")
  void usesRefundRequestedAtForRequestedOrder() {
    Order order = order(50_000);
    order.requestRefund("일정 변경", REQUESTED_AT);
    when(refundPolicyPersistencePort.findAllByStoreId(STORE_ID)).thenReturn(policies());

    OrderRefundCalculation calculation = resolver.resolve(order, PROCESSING_AT);

    assertEquals(50_000, calculation.paidAmount());
    assertEquals(50_000, calculation.refundAmount());
    assertEquals(100, calculation.refundRate());
    assertEquals(REQUESTED_AT, calculation.calculationBaseAt());
    assertEquals(
        OrderRefundCalculationBasis.REFUND_REQUESTED_AT, calculation.calculationBasis());
  }

  @Test
  @DisplayName("결제완료 주문의 판매자 직접 환불은 현재 처리 시각 기준으로 계산한다")
  void usesCurrentTimeForPaidOrder() {
    Order order = order(50_000);
    when(refundPolicyPersistencePort.findAllByStoreId(STORE_ID)).thenReturn(policies());

    OrderRefundCalculation calculation = resolver.resolve(order, PROCESSING_AT);

    assertEquals(40_000, calculation.refundAmount());
    assertEquals(80, calculation.refundRate());
    assertEquals(PROCESSING_AT, calculation.calculationBaseAt());
    assertEquals(OrderRefundCalculationBasis.CURRENT_TIME, calculation.calculationBasis());
  }

  @Test
  @DisplayName("환불 요청 상태인데 요청 시각이 없으면 도메인 정합성 오류로 처리한다")
  void rejectsMissingRefundRequestedAt() {
    Order order = order(50_000);
    ReflectionTestUtils.setField(order, "status", OrderStatus.REFUND_REQUESTED);

    BaseException exception =
        assertThrows(BaseException.class, () -> resolver.resolve(order, PROCESSING_AT));

    assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
  }

  @Test
  @DisplayName("환불 불가능 상태는 계산하지 않는다")
  void rejectsNotRefundableOrderStatus() {
    Order order = order(50_000);
    order.markPickedUp();

    BaseException exception =
        assertThrows(BaseException.class, () -> resolver.resolve(order, PROCESSING_AT));

    assertEquals(OrderErrorCode.ORDER_STATUS_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("기존 계산기의 절사 규칙을 그대로 사용한다")
  void keepsTruncationRule() {
    Order order = order(10_001);
    when(refundPolicyPersistencePort.findAllByStoreId(STORE_ID)).thenReturn(policies());

    OrderRefundCalculation calculation = resolver.resolve(order, PROCESSING_AT);

    assertEquals(8_000, calculation.refundAmount());
    assertEquals(80, calculation.refundRate());
  }

  private Order order(long paidAmount) {
    return Order.create(
        STORE_ID,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "P3-20300915-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
        "초코 케이크",
        "초코 시트",
        paidAmount,
        PICKUP_AT);
  }

  private List<StoreRefundPolicy> policies() {
    return List.of(policy(7, 100), policy(5, 80), policy(3, 50));
  }

  private StoreRefundPolicy policy(int daysBeforePickup, int refundRate) {
    return StoreRefundPolicy.create(STORE_ID, daysBeforePickup, refundRate, 0);
  }
}
